package New;

import New.AST.*;
import Utils.BiMap;
import Utils.DefaultMap;

import java.util.*;

public class Penalty {

    private static final Map<Element, Double> classPenaltyFunctionMap;

    private final BiMap<Variable, Variable, Double> variableMatchScores;

    static {
        classPenaltyFunctionMap =
                new DefaultMap<>(x -> x.pathElement1.node.getClass() == x.pathElement2.node.getClass() ? 1.0 : 0.0);

        consider(Branch.class, null, .5);
        consider(Branch.class, Loop.class, .1);
        consider(Branch.class, MethodCall.class, .001);

        consider(Loop.class, null, .2);
        consider(Loop.class, MethodCall.class, .002);

        consider(Block.class, null, 1);

        consider(Value.class, null, .3);
        consider(Value.class, Assignment.class, .05);
        consider(Value.class, MethodCall.class, .5); // i should differentiate different kinds of methodCalls
        consider(Value.class, ArithmeticOperator.class, .3);
        consider(Value.class, BooleanOperator.class, .3);
        consider(Value.class, CompareOperator.class, .3);

        consider(Assignment.class, null, 0);
        consider(Assignment.class, MethodCall.class, .1); // i should differentiate different kinds of methodCalls
        consider(Value.class, ArithmeticOperator.class, .05);
        consider(Value.class, BooleanOperator.class, .05);
        consider(Value.class, CompareOperator.class, .05);

        // i should differentiate different kinds of methodCalls
        consider(MethodCall.class, null, .05);
        consider(Value.class, ArithmeticOperator.class, .8);
        consider(Value.class, BooleanOperator.class, .8);
        consider(Value.class, CompareOperator.class, .8);

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
        this.variableMatchScores = new BiMap<>(variableMatchScores);
    }

    public Penalty() {
        variableMatchScores = null;
    }

    public double getPenalty(Path.Element pathElement1, Path.Element pathElement2) {
        double penalty = classPenaltyFunctionMap.get(new Element(pathElement1, pathElement2));

        if (pathElement1.role != pathElement2.role)
            penalty *= .1;

        if (pathElement1 != null || pathElement2 != null) {
            Node node1 = pathElement1.node;
            Node node2 = pathElement2.node;

            penalty *= summaryPenalty(node1.getThisSummary(), node2.getThisSummary(),
                    .9, 1, 1);
            penalty *= summaryPenalty(pathElement1.adjunctSummary, pathElement2.adjunctSummary,
                    .9, .7, .4);
            // this introduces correlation because we have both in-the-path and off-the-path values, so we include
            // off-the-path values twice overally (because we have it alreadt in th adjunctSummary).
            penalty *= summaryPenalty(node1.getAggregatedSummary(), node2.getAggregatedSummary(),
                    .9, .7, .5);

            if (variableMatchScores != null
                    && node1 instanceof Value && node2 instanceof Value
                    && ((Value) node1).getVariable() != null && ((Value) node2).getVariable() != null)
                // check this and test this (this may fuck everything and result in all variables being skipped because
                // a variable value is essentially a value of a sequence mathcing which is much lower than a node
                penalty *= Math.exp(-variableMatchScores.get(((Value) node1).getVariable(), ((Value) node2).getVariable()));
        }

        return penalty;
    }

    private static double summaryPenalty(Node.Summary summary1, Node.Summary summary2,
                                         double typeMin, double classMin, double sourceMin) {
        double penalty = 1;
        penalty *= setSimilarity(summary1.getNodeTypes(), summary2.getNodeTypes()) * (1 - typeMin) + typeMin;
        penalty *= setSimilarity(summary1.getNodeClasses(), summary2.getNodeClasses()) * (1 - classMin) + classMin;
        penalty *= setSimilarity(summary1.getNodeSources(), summary2.getNodeSources()) * (1 - sourceMin) + sourceMin;
        return penalty;
    }

    private static <T> double setSimilarity(Set<T> set1, Set<T> set2) {
        if (set1.isEmpty() && set2.isEmpty())
            return 1;
        Set<T> intersection = Set.copyOf(set1);
        intersection.retainAll(set2);
        Set<T> union = Set.copyOf(set1);
        union.addAll(set2);
        return (double) intersection.size() / union.size();
    }

    private static void consider(Class<? extends Node> class1, Class<? extends Node> class2, double value) {
        classPenaltyFunctionMap.put(new Element(class1, class2), value);
    }

    private static class Element {
        private final Set<Class<?>> values;
        private final Path.Element pathElement1;
        private final Path.Element pathElement2;

        public Element(Path.Element pathElement1, Path.Element pathElement2) {
            values = Set.of(pathElement1.node.getClass(), pathElement2.node.getClass());
            this.pathElement1 = pathElement1;
            this.pathElement2 = pathElement2;
        }

        public Element(Class<?> pathElement1NodeClass, Class<?> pathElement2NodeClass) {
            values = Set.of(pathElement1NodeClass, pathElement2NodeClass);
            pathElement1 = null;
            pathElement2 = null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Element && values == ((Element) o).values;
        }

        @Override
        public int hashCode() {
            return values.hashCode();
        }
    }
}
