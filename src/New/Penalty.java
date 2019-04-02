package New;

import New.AST.*;
import Utils.BiMap;
import Utils.DefaultMap;
import Utils.Pair;

import java.util.*;
import java.util.function.Function;

public class Penalty {

    // this should be private
    public static final Map<Element, Double> classPenaltyFunctionMap;

    private final BiMap<Variable, Variable, Double> variableMatchScores;

    // the model to predict this penalty function can be conditioned on the programs!
    static {
        // this considers the simple local semantics of node classes
        classPenaltyFunctionMap =
                new DefaultMap<>(x ->
                        dot(x.pathElement1, y -> y.node.getClass()) == dot(x.pathElement2, y -> y.node.getClass())
                                ? 1.0 : 0.0);

        consider(Branch.class, null, 1);
        consider(Branch.class, Loop.class, .1);
        consider(Branch.class, MethodCall.class, .001);

        consider(Loop.class, null, .2);
        consider(Loop.class, MethodCall.class, .002);

        consider(Block.class, null, 1);

        consider(Value.class, null, .01);
        consider(Value.class, Assignment.class, .05);
        consider(Value.class, MethodCall.class, .5); // i should differentiate different kinds of methodCalls
        consider(Value.class, ArithmeticOperator.class, .3);
        consider(Value.class, BooleanOperator.class, .3);
        consider(Value.class, CompareOperator.class, .3);

        consider(Assignment.class, null, .1);
        consider(Assignment.class, MethodCall.class, .2); // i should differentiate different kinds of methodCalls
        consider(Assignment.class, ArithmeticOperator.class, .05);
        consider(Assignment.class, BooleanOperator.class, .05);
        consider(Assignment.class, CompareOperator.class, .05);

        // i should differentiate different kinds of methodCalls
        consider(MethodCall.class, null, .05);
        consider(MethodCall.class, ArithmeticOperator.class, .8);
        consider(MethodCall.class, BooleanOperator.class, .8);
        consider(MethodCall.class, CompareOperator.class, .8);

        consider(CompareOperator.class, null, .2);
        consider(BooleanOperator.class, null, .2);
        consider(ArithmeticOperator.class, null, .2);
        consider(CompareOperator.class, BooleanOperator.class, .4);
        consider(CompareOperator.class, ArithmeticOperator.class, .4);
        consider(BooleanOperator.class, ArithmeticOperator.class, .4);

        consider(Break.class, null, .03);
        consider(Break.class, Continue.class, .5);

        consider(Continue.class, null, .03);

        consider(ArgumentsBlock.class, null, 1);
    }

    public Penalty(BiMap<Variable, Variable, Double> variableMatchScores) {
        if (variableMatchScores == null)
            this.variableMatchScores = null;
        else
            this.variableMatchScores = new BiMap<>(variableMatchScores);
    }

    public Penalty() {
        variableMatchScores = null;
    }

    // search for int t = 1 and delete them everywhere.
    // incorporate ordinal as well (e.g. by considering summary of before and after nodes)
    // this considers the subtree semantics, except for the path that occuurs in this session (so adjunct) (so "simple
    // local semantics" + semantics of adjunct subtree. i can find a method to estimate any of the two (or learn their
    // parameters), or i can estimate (or learn) this whole thing(local+adjunct) as a whole
    public double getPenalty(Path.Element pathElement1, Path.Element pathElement2) {
        double penalty = classPenaltyFunctionMap.get(new Element(pathElement1, pathElement2));

        if (penalty > 0 && pathElement1 != null && pathElement2 != null) {
            if (pathElement1.role != pathElement2.role)
                penalty *= .1;

            Node node1 = pathElement1.node;
            Node node2 = pathElement2.node;

            if (!(node1 instanceof Block || node1 instanceof ArgumentsBlock)
                    || !(node2 instanceof Block || node2 instanceof ArgumentsBlock)) {
                penalty *= summaryPenalty(node1.getThisSummary(), node2.getThisSummary(),
                        .9, 1, 0.1, 1);
                // compute adjunct for each role (set of children for one role) and if the next element in path belong
                // to a role, the adjunt for that role is not computed (but here instead of one adjunct we have a list
                // of adjunct for each role)
                penalty *= summaryPenalty(pathElement1.adjunctSummary, pathElement2.adjunctSummary,
                        .9, 0.1, .4, .6);
                // this introduces correlation because we have both in-the-path and off-the-path values, so we include
                // off-the-path values twice overally (because we have it alreadt in th adjunctSummary).
                penalty *= summaryPenalty(node1.getAggregatedSummary(), node2.getAggregatedSummary(),
                        .9, .7, .5, .6);
            }

            if (node1 instanceof Value && node2 instanceof Value
                    && ((Value) node1).getVariable() == null
                    && ((Value) node2).getVariable() == null
                    && !((Value) node1).getText().equals(((Value) node2).getText()))
                penalty *= .95;

            if (variableMatchScores != null
                    && node1 instanceof Value && node2 instanceof Value
                    && ((Value) node1).getVariable() != null && ((Value) node2).getVariable() != null)
                // check this and test this (this may fuck everything and result in all variables being skipped because
                // a variable value is essentially a value of a sequence mathcing which is much lower than a node
                penalty *= Math.exp(-variableMatchScores
                        .get(((Value) node1).getVariable(), ((Value) node2).getVariable()));
        }

        return penalty;
    }

    private double summaryPenalty(Node.Summary summary1, Node.Summary summary2,
                                  double typeMin, double classMin, double sourceMin, double variableMin) {
        double penalty = 1;
        penalty *= setSimilarity(summary1.getNodeTypes(), summary2.getNodeTypes()) * (1 - typeMin) + typeMin;
        penalty *= setSimilarity(summary1.getNodeClasses(), summary2.getNodeClasses()) * (1 - classMin) + classMin;
        penalty *= setSimilarity(summary1.getNodeSources(), summary2.getNodeSources()) * (1 - sourceMin) + sourceMin;

        if (variableMatchScores != null) {
            // better algorithm?
            BiMap<Variable, Variable, Double> existingVarsMatchScores = new BiMap<>();
            for(Variable var1 : summary1.getNodeVariables())
                for(Variable var2 : summary2.getNodeVariables())
                    existingVarsMatchScores.put(var1, var2, variableMatchScores.get(var1,var2));
            Set<Variable> summary2VarMatches = new HashSet<>();
            for (Pair<Variable, Variable> varMatch : Program.getGreedyMatches(existingVarsMatchScores))
                if (summary2.getNodeVariables().contains(varMatch.getSecond()))
                    summary2VarMatches.add(varMatch.getSecond());
            penalty *= setSimilarity(summary1.getNodeVariables(), summary2VarMatches) * (1 - variableMin) + variableMin;
        }
        return penalty;
    }

    private static <T> double setSimilarity(Set<T> set1, Set<T> set2) {
        if (set1.isEmpty() && set2.isEmpty())
            return 1;
        Set<T> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        Set<T> union = new HashSet<>(set1);
        union.addAll(set2);
        return (double) intersection.size() / union.size();
    }

    private static <U, V> V dot(U obj, Function<U, V> field) {
        return obj == null ? null : field.apply(obj);
    }

    private static void consider(Class<? extends Node> class1, Class<? extends Node> class2, double value) {
        classPenaltyFunctionMap.put(new Element(class1, class2), value);
    }

    // this should be private
    public static class Element {
        private final Set<Class<?>> values;
        private final Path.Element pathElement1;
        private final Path.Element pathElement2;

        public Element(Path.Element pathElement1, Path.Element pathElement2) {
            values = new HashSet<>();
            values.add(dot(pathElement1, x -> x.node.getClass()));
            values.add(dot(pathElement2, x -> x.node.getClass()));

            this.pathElement1 = pathElement1;
            this.pathElement2 = pathElement2;
        }

        public Element(Class<?> pathElement1NodeClass, Class<?> pathElement2NodeClass) {
            values = new HashSet<>();
            values.add(pathElement1NodeClass);
            values.add(pathElement2NodeClass);

            pathElement1 = null;
            pathElement2 = null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Element && values.equals(((Element) o).values);
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }
    }
}
