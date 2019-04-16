package New;

import New.AST.*;
import New.AST.SnippetConverter.Snippet;
import New.Optimization.Parameters;
import Utils.BiMap;
import Utils.DefaultMap;
import Utils.NodeHelper;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

//support lambda and anonymous classes

public class NodeMatcher {

    private final Snippet snippetP;
    private final Snippet snippetQ;
    private final BiMap<Variable, Variable, Double> varMatchScores;

    public NodeMatcher(Snippet snippetP, Snippet snippetQ, BiMap<Variable, Variable, Double> varMatchScores) {
        this.snippetP = snippetP;
        this.snippetQ = snippetQ;
        if (varMatchScores == null)
            this.varMatchScores = null;
        else
            this.varMatchScores = new BiMap<>(varMatchScores);
    }

    private BiMap<Node, Node, Double> nodeMatchScores;

    // PERFORMANCE
    public static BiMap<Variable, Variable, Double>
    computeVariableMatchScores(BiMap<Node, Node, Double> nodeMatchScores) {
        List<BiMap.Entry<Node, Node, Double>> varNodes = nodeMatchScores.getEntries().stream()
                .filter(x -> x.getKey1() instanceof Value && ((Value) x.getKey1()).getVariable() != null
                        && x.getKey2() instanceof Value && ((Value) x.getKey2()).getVariable() != null)
                .collect(Collectors.toList());

        Function<Stream<BiMap.Entry<Value, Value, Double>>, BiMap<Variable, Variable, Double>> getMatchMap =
                nodes ->
                        nodes.collect(BiMap.toMap(BiMap.Entry::getKey1, x -> x.getKey2().getVariable(),
                                x -> x.getValue(), (a, b) -> Math.max(a, b))).getEntries().stream()
                                .collect(BiMap.groupingBy(
                                        x -> x.getKey1().getVariable(), BiMap.Entry::getKey2,
                                        Collectors.averagingDouble(x -> x.getValue())));

        BiMap<Variable, Variable, Double> PQMatchMap = getMatchMap.apply(varNodes.stream()
                .map(x -> new BiMap.Entry<>((Value) x.getKey1(), (Value) x.getKey2(), x.getValue())));
        BiMap<Variable, Variable, Double> QPMatchMap = getMatchMap.apply(varNodes.stream()
                .map(x -> new BiMap.Entry<>((Value) x.getKey2(), (Value) x.getKey1(), x.getValue())));

        return PQMatchMap.getEntries().stream().collect(BiMap.toMap(BiMap.Entry::getKey1, BiMap.Entry::getKey2,
                x -> (x.getValue() + QPMatchMap.get(x.getKey2(), x.getKey1())) / 2, (a, b) -> a));
    }

    private static double getNullValue(Class<? extends Node> clazz) {
        if (clazz == Branch.class)
            return Parameters.NodeMatcher.branchNull.getValue();
        if (clazz == Loop.class)
            return Parameters.NodeMatcher.loopNull.getValue();
        if (clazz == Block.class)
            return Parameters.NodeMatcher.blockNull.getValue();
        if (clazz == Value.class)
            return Parameters.NodeMatcher.valueNull.getValue();
        if (clazz == Assignment.class)
            return Parameters.NodeMatcher.asisgnmentNull.getValue();
        if (clazz == MethodCall.class)
            return Parameters.NodeMatcher.methodCallNull.getValue();
        if (clazz == CompareOperator.class)
            return Parameters.NodeMatcher.compareOperatorNull.getValue();
        if (clazz == BooleanOperator.class)
            return Parameters.NodeMatcher.booleanOperatorNull.getValue();
        if (clazz == ArithmeticOperator.class)
            return Parameters.NodeMatcher.arithmeticOperatorNull.getValue();
        if (clazz == Break.class)
            return Parameters.NodeMatcher.breakNull.getValue();
        if (clazz == Continue.class)
            return Parameters.NodeMatcher.continueNull.getValue();
        if (clazz == ArgumentsBlock.class)
            return Parameters.NodeMatcher.argumentsBlockNull.getValue();
        throw new RuntimeException("Invalid node.");
    }

    // it is important to note: in this algorithm, (if i use an algorithm to convert m Map to a node matching (like the
    // greedy algorithm i have)) it is possible to match two nodes that are not children of same parent. BUT for the
    // chil part (tree assignment) i do limit myself (exactly like the way i limit myself in par part (sequence
    // alignment)) that if two nodes are match, their children can only be matched among themselves.

    // VERY IMPORTANT: one problem is that if a node has parent, its score is probably more that a node without parent,
    // even if the without parent pair is actually more match (same with children as well)

    // the params: l,r,chil,par (but here i have assumed that l and r are simple 1-0 things) (note that actually the
    // weight of r and l (relative to each other) is also important but since the two matrices are created independantly
    // the weight can be incorporated in them (unless we stick to the 0-1 setting))

    // a good thing about an iteratie algorihtm is that it is flexible, if we have less time, we still can do it but
    // with less accuracy

    // use this as clone detector
    public BiMap<Node, Node, Double> getNodeMatchScores() {
        if (nodeMatchScores != null)
            return new BiMap<>(nodeMatchScores);
        Map<Node, Node.Role> roles = new DefaultMap<>(n ->
                n.getParent() == null ? Node.Role.Root : n.getParent().getChild(n).role);
        BiMap<Node, Node, Double> m = new BiMap<>((a, b) -> 1.0);
        // incorporate vairbale matching in l
        // generally use other ideas to create a better l
        BiMap<Node, Node, Double> l;
        if (varMatchScores == null)
            l = new BiMap<>((a, b) -> a.getClass().equals(b.getClass()) ?
                    a instanceof Value && b instanceof Value && (((Value) a).getVariable() == null) !=
                            (((Value) b).getVariable() == null) ? 0.0 : 1.0 : 0.0);
        else
            l = new BiMap<>((a, b) -> {
                if (a.getClass() != b.getClass())
                    return 0.0;
                if (!(a instanceof Value))
                    return 1.0;
                if ((((Value) a).getVariable() == null) != (((Value) b).getVariable() == null))
                    return 0.0;
                if (((Value) a).getVariable() == null && ((Value) b).getVariable() == null)
                    return 1.0;
                return varMatchScores.get(((Value) a).getVariable(), ((Value) b).getVariable());
            });
//            l = new BiMap<>((a, b) ->
//                    Penalty.classPenaltyFunctionMap
//                            .get(new Penalty.Element(getElement(a, roles), getElement(b, roles))));

        BiMap<Node.Role, Node.Role, Double> r = new BiMap<>((r1, r2) -> r1 == r2 ? 1.0 : 0.0);

        // nullVal cannot be more than .5
        // find a better approach for nullValue than this (one idea is to have specific nullValue for each class (like how I had in the preivous appoach)
        double nullValue = Parameters.NodeMatcher.generalNull.getValue();
        double nullValueStp = Parameters.NodeMatcher.generalNullStep.getValue();
        double nullValueMin = Parameters.NodeMatcher.generalNullMin.getValue();

        Map<Node, Path> partPaths = new HashMap<>();
        getPartPaths(snippetP, roles, partPaths);
        getPartPaths(snippetQ, roles, partPaths);

        // instead of n, compare m and stop when it converges
        int n = 10;
        while (n-- > 0) {
            // if i add any parameter to this method, i should consider dividing it to toDivide (if applicale)
            m = iterate(snippetP.getRoot(), snippetQ.getRoot(),
                    // for learning par and chil, one good way to speed the learning up is to assume they are equal
                    m, l, r, partPaths, roles,
                    Parameters.NodeMatcher.parentWeight.getValue(),
                    Parameters.NodeMatcher.childWeight.getValue(),
                    //this inline if is to make sure i do not affect variable similarity if it is already fed. however, this whole thing is messed up and should be centralized in one function (say compute_l)
                    varMatchScores == null ? Parameters.NodeMatcher.variableMatchWeight.getValue() : 0,
                    getVarNodeSet(snippetP), getVarNodeSet(snippetQ), nullValue);

//            temp_toDivide = m.values().stream().mapToDouble(x -> x).max().getAsDouble();
//            divideValues(m, temp_toDivide);
//            divideValues(l, temp_toDivide);
//            divideValues(r, temp_toDivide);
//            nullValue /= temp_toDivide;

            nullValue = Math.max(nullValueMin, nullValue - nullValueStp);

            System.out.println(n + 1 + " finished.");
//            System.out.println(temp_toDivide);
        }

        // do not use greedy matching. maybe each node can be matched no more than 1 nodes. also have in mind that
        // i must have a threshold for matching and all records below that threshold should be removed before any
        // matching algorithm. (so for two programs it may be the case that there are nodes in both program that are
        // not matched.
        nodeMatchScores = m;
        return new BiMap<>(nodeMatchScores);
    }

    // i need this to improve performance significantly (the reason i do not use the same technique for par is that
    // that does not take that much. BUT IT CERTAINLY HELPS IN THAT CASE TOO (PERFORMANCE))
    private BiMap<Set<Node>, Set<Node>, Double> treeAlignmentScores;

    private BiMap<Node, Node, Double> nodeSubtreeMin;

    private BiMap<Node, Node, Double> pathAlignmentScores;

    private final Map<Node, Integer> subtreeSize = new DefaultMap<>(
            node -> {
                int size = 1;
                for (Node.Child c : node.getChildren())
                    size += this.subtreeSize.get(c.node);
                return size;
            });

    private BiMap<Node, Node, Double> iterate(Node rootP, Node rootQ, BiMap<Node, Node, Double> m,
                                              BiMap<Node, Node, Double> l, BiMap<Node.Role, Node.Role, Double> r,
                                              Map<Node, Path> partPaths, Map<Node, Node.Role> roles,
                                              double par, double chil, double varMatch,
                                              // varP and varQ contain only Value nodes that are var or input, not literals
                                              Set<Value> varsP, Set<Value> varsQ,
                                              // this nullVal can actually be a map like m that can be learned (think about how what
                                              //      happens that causes a node to match nothing)
                                              Double nullVal) {
        treeAlignmentScores = null;
        nodeSubtreeMin = null;
        pathAlignmentScores = null;

        BiMap<Node, Node, Double> result = new BiMap<>();
        NodeHelper.bfsOnNode(rootP, nP -> {
            // i can use hueristics to avoid doing this for all pairs of nP&nQ (in penalty class, what pairs are zero?)
            NodeHelper.bfsOnNode(rootQ, nQ -> {
//                double parScore = -SequenceDistanceCalculator.calculate(partPaths.get(nP), partPaths.get(nQ),
//                        (eP, eQ) -> {
//                            if (eP == null || eQ == null)
//                                return -nullVal;
//                            return -par.apply(m.get(eP.node, eQ.node) + r.get(eP.role, eQ.role));
//                        }).score;

                int parMaxSize = Math.max(partPaths.get(nP).size(), partPaths.get(nQ).size());
                int parMinSize = Math.min(partPaths.get(nP).size(), partPaths.get(nQ).size());
                double parN = (parMaxSize + parMinSize) / 2.0;// parMinSize + nullVal * (parMaxSize - parMinSize);
                double parScore;
                if (parN == 0)
                    parScore = 1;
                else
                    parScore = -partPathAlignmentHelper(nP, nQ, partPaths,
                            (eP, eQ) -> {
                                if (eP == null || eQ == null)
                                    return getNullValue(eP == null ? eQ.node.getClass() : eP.node.getClass());
                                // maybe the nullVal map for parents should be different to that of the children
//                                    return -Penalty.classPenaltyFunctionMap.get(new Penalty.Element(eP, eQ)) / 2;
//                                    return -nullVal;
                                // i removed r from here cuz i add it in local score
                                return -m.get(eP.node, eQ.node);
                            }, roles) / parN;


//                double chilScore = -temp_simpleNodeMatchFinderHelper(nP, nQ, (eP, eQ) -> {
//                    if (eP == null || eQ == null)
//                        return -nullVal;
//                    return -chil.apply(m.get(eP.node, eQ.node) + r.get(eP.role, eQ.role));
//                }, roles);

                int chilMaxSize = Math.max(subtreeSize.get(nP) - 1, subtreeSize.get(nQ) - 1);
                int chilMinSize = Math.min(subtreeSize.get(nP) - 1, subtreeSize.get(nQ) - 1);
                double chilN = (chilMaxSize + chilMinSize) / 2.0;// chilMinSize + nullVal * (chilMaxSize - chilMinSize);
                double chilScore;
                if (chilN == 0)
                    chilScore = 1;
                else
                    chilScore = -partTreeAlignmentHelper(nP, nQ,
                            (eP, eQ) -> {
                                if (eP == null || eQ == null)
                                    return getNullValue(eP == null ? eQ.node.getClass() : eP.node.getClass());
//                                    return -Penalty.classPenaltyFunctionMap.get(new Penalty.Element(eP, eQ)) / 2;
//                                    return -nullVal;
                                // i removed r from here cuz i add it in local score
                                return -m.get(eP.node, eQ.node);
                            }, roles) / chilN;

                // maybe weighted average for localScore instead of just summing and dividing?
                // incorporate ordinal in local information
                double localScore = l.get(nP, nQ) + r.get(roles.get(nP), roles.get(nQ));
                double localScoreN = 2;
                double possibleLocalScore = -1;
                // any bettr idea for this part? (think about it, i think it has problems)
                // add something similar so that for method calls if names are same the score is highrt
                // here i am doing very similar to computing varMatchScores. so it is better to leverage this fatc.
                if (nP instanceof Value && nQ instanceof Value) {
                    // a better way of incorporating source? (i already incorporate literal vs. non-literal in l so i commented these out)
//                    localScore += ((Value) nP).getSource() == ((Value) nQ).getSource() ? 1.0 : 0.0;
//                    localScoreN++;

                    Map<Node, Double> PQMatchMap = new DefaultMap<>(x -> Double.NEGATIVE_INFINITY);
                    Map<Node, Double> QPMatchMap = new DefaultMap<>(x -> Double.NEGATIVE_INFINITY);
                    Collection<Value> goodVarsP = varsP.stream()
                            .filter(x -> x.getVariable() == ((Value) nP).getVariable() && x != nP)
                            .collect(Collectors.toSet());
                    Collection<Value> goodVarsQ = varsQ.stream()
                            .filter(x -> x.getVariable() == ((Value) nQ).getVariable() && x != nQ)
                            .collect(Collectors.toSet());
                    for (Value varP : goodVarsP)
                        for (Value varQ : goodVarsQ) {
                            PQMatchMap.put(varP, Math.max(PQMatchMap.get(varP), m.get(varP, varQ)));
                            QPMatchMap.put(varQ, Math.max(QPMatchMap.get(varQ), m.get(varP, varQ)));
                        }

                    possibleLocalScore = localScore +
                            varMatch *
                                    (PQMatchMap.entrySet().stream().mapToDouble(x -> x.getValue())
                                            .average().orElse(nullVal) +
                                            QPMatchMap.entrySet().stream().mapToDouble(x -> x.getValue())
                                                    .average().orElse(nullVal)) / 2;
                    possibleLocalScore /= localScoreN + varMatch;//(PQMatchMap.isEmpty() ? 0 : 1);
//                    localScore += PQMatchMap.entrySet().stream().mapToDouble(x -> x.getValue()).sum()
//                            + QPMatchMap.entrySet().stream().mapToDouble(x -> x.getValue()).sum();
//                    localScoreN += PQMatchMap.size() + QPMatchMap.size();
                }
                if (possibleLocalScore == -1)
                    localScore /= localScoreN;
                else
                    localScore = possibleLocalScore;
//                localScore = Math.max(localScore / localScoreN, possibleLocalScore);

//                double dived = 100000.0;
//                parScore = (int) (parScore * dived) / dived;
//                chilScore = (int) (chilScore * dived) / dived;
//                localScore = (int) (localScore * dived) / dived;

                // PERFORMANCE (MAYBE CACHING HELPS)
                double score = (par * parScore + chil * chilScore + localScore) / (par + chil + 1);

                // i should add an statement here to do automatic variable matching as well (e.g. if two m for two values
                // are higher, and the variables of these two values are the same as those, score should be higher here too)
                result.put(nP, nQ, score);
                if (score < 0 || score > 1 || parScore < 0 || parScore > 1
                        || chilScore < 0 || chilScore > 1 || localScore < 0 || localScore > 1)
                    throw new RuntimeException("Logical error!");

//                System.out.println(treeAlignmentScores.size());

                return true;
            });
            return true;
        });

        return result;
    }

    private double partPathAlignmentHelper(Node nP, Node nQ, Map<Node, Path> partPaths,
                                           BiFunction<Path.Element, Path.Element, Double> penalty,
                                           Map<Node, Node.Role> roles) {
        if (pathAlignmentScores == null)
            pathAlignmentScores = new BiMap<>(
                    (n1, n2) -> pathAlignment(n1, n2, partPaths, penalty, roles, pathAlignmentScores));
        return pathAlignmentScores.get(nP.getParent(), nQ.getParent());
    }

    // change this so that big is better
    private static double pathAlignment(Node nP, Node nQ, Map<Node, Path> partPaths,
                                        BiFunction<Path.Element, Path.Element, Double> penalty,
                                        Map<Node, Node.Role> roles,
                                        BiMap<Node, Node, Double> memeoise) {
        if (nP == null && nQ == null)
            return 0;
        double score = Double.POSITIVE_INFINITY;

        Node nPNext = null;
        if (nP != null && !partPaths.get(nP).isEmpty())
            nPNext = partPaths.get(nP).get(partPaths.get(nP).size() - 1).node;
        Node nQNext = null;
        if (nQ != null && !partPaths.get(nQ).isEmpty())
            nQNext = partPaths.get(nQ).get(partPaths.get(nQ).size() - 1).node;

        if (nP != null && nQ != null)
            score = Math.min(score,
                    penalty.apply(getElement(nP, roles), getElement(nQ, roles))
                            + memeoise.get(nPNext, nQNext));
        if (nP != null)
            score = Math.min(score,
                    penalty.apply(getElement(nP, roles), null) + memeoise.get(nPNext, nQ));
        if (nQ != null)
            score = Math.min(score,
                    penalty.apply(null, getElement(nQ, roles)) + memeoise.get(nP, nQNext));
        return score;

    }

    // tries to minimize score
    // maybe a similar algorithm for parents (instead of sequence alignment) is better??
    // test if this is correctly implemented
    private double simpleNodeMatchFinderHelper(Node rP, Node rQ,
                                               BiFunction<Path.Element, Path.Element, Double> penalty,
                                               Map<Node, Node.Role> roles,
                                               double nullVal) {
        // bad practice
        var penaltyContext = new Object() {
            BiFunction<Path.Element, Path.Element, Double> usedPenalty;
        };

        if (nodeSubtreeMin == null)
            nodeSubtreeMin = new BiMap<>(
                    (n, s) -> simpleNodeMatchFinder(n, s, penaltyContext.usedPenalty,
                            roles, nodeSubtreeMin, nullVal));

        Map<Node, Double> nodeBestScores = new DefaultMap<>(x -> Double.POSITIVE_INFINITY);

        penaltyContext.usedPenalty = penalty;
        NodeHelper.bfsOnNode(rP, nP -> {
            if (nP == rP)
                return true;
            double newScore = rQ.getChildren().stream()
                    .map(x -> nodeSubtreeMin.get(nP, x.node)).mapToDouble(x -> x).min().orElse(nullVal);
            if (newScore < nodeBestScores.get(nP))
                nodeBestScores.put(nP, newScore);
            return true;
        });

        penaltyContext.usedPenalty = (a, b) -> penalty.apply(b, a);
        NodeHelper.bfsOnNode(rQ, nQ -> {
            if (nQ == rQ)
                return true;
            double newScore = rP.getChildren().stream()
                    .map(x -> nodeSubtreeMin.get(nQ, x.node)).mapToDouble(x -> x).min().orElse(nullVal);
            if (newScore < nodeBestScores.get(nQ))
                nodeBestScores.put(nQ, newScore);
            return true;
        });

        return nodeBestScores.entrySet().stream().mapToDouble(x -> x.getValue()).sum();
    }

    private static double simpleNodeMatchFinder(Node node, Node subtree,
                                                BiFunction<Path.Element, Path.Element, Double> penalty,
                                                Map<Node, Node.Role> roles,
                                                BiMap<Node, Node, Double> memoise,
                                                double nullValue) {
        double score1 = Math.min(nullValue,
                penalty.apply(getElement(node, roles), getElement(subtree, roles)));
        Optional<Double>
                score2 = subtree.getChildren().stream().map(x -> memoise.get(node, x.node)).min(Double::compareTo);
        return score2.map(aDouble -> Math.min(score1, aDouble)).orElse(score1);
    }

    private double partTreeAlignmentHelper(Node rP, Node rQ,
                                           BiFunction<Path.Element, Path.Element, Double> penalty,
                                           Map<Node, Node.Role> roles) {
        // make sure that set is compared regarding its content not its reference
        if (treeAlignmentScores == null)
            treeAlignmentScores = new BiMap<>((s1, s2) -> treeAlignment(s1, s2,
                    penalty, treeAlignmentScores, roles));
        // because part
        return treeAlignmentScores.get(
                new HashSet<>(rP.getChildren().stream().map(x -> x.node).collect(Collectors.toList())),
                new HashSet<>(rQ.getChildren().stream().map(x -> x.node).collect(Collectors.toList())));
    }

    // one way to test it is to compare it to the sequence matcher (if trees are sequences, the two should be the same)
    // make sure this algorithm (without optimization heuristics) is optimum (explores all possible combinations)
    // i can use heuristics to avoid exploring all possiblities
    // like seq alignment, this tries to minimize the score
    // one way to estimate this is gumtreediff (or things like that)
    // change this so that big is better
    private static double treeAlignment(Set<Node> sP, Set<Node> sQ,
                                        BiFunction<Path.Element, Path.Element, Double> penalty,
                                        BiMap<Set<Node>, Set<Node>, Double> scores,
                                        Map<Node, Node.Role> roles) {
        double score = Double.POSITIVE_INFINITY;
        if (sP.isEmpty() && sQ.isEmpty())
            return 0;
        if (!sP.isEmpty())
            score = computeTreeAlignmentSuboptimalScore(sP, sQ, penalty, (a, b) -> scores.get(a, b), roles);
        if (!sQ.isEmpty()) {
            double newScore = computeTreeAlignmentSuboptimalScore(sQ, sP,
                    (a, b) -> penalty.apply(b, a), (a, b) -> scores.get(b, a), roles);
            if (newScore < score)
                score = newScore;
        }
        return score;
    }

    private static double computeTreeAlignmentScore(Set<Node> mainSet, Set<Node> iterationSet,
                                                    BiFunction<Path.Element, Path.Element, Double> penalty,
                                                    BiFunction<Set<Node>, Set<Node>, Double> getScores,
                                                    Map<Node, Node.Role> roles) {
        Node mainNode = mainSet.iterator().next();
        Set<Node> newMainSet = unionSet(getChildSet(mainNode), mainSet);
        newMainSet.remove(mainNode);
        double score = penalty.apply(getElement(mainNode, roles), null)
                + getScores.apply(newMainSet, iterationSet);

        for (Node iterationNode : iterationSet) {
            newMainSet = new HashSet<>(mainSet);
            newMainSet.remove(mainNode);
            Set<Node> newIterationSet = new HashSet<>(iterationSet);
            newIterationSet.remove(iterationNode);
            double penaltyScore =
                    penalty.apply(getElement(mainNode, roles), getElement(iterationNode, roles));
            // this if makes this suboptimal
//            if (mainNode.getClass() != iterationNode.getClass() && roles.get(mainNode) != roles.get(iterationNode))
//                continue;
            penaltyScore += getScores.apply(getChildSet(mainNode), getChildSet(iterationNode))
                    + getScores.apply(newMainSet, newIterationSet);
            if (penaltyScore < score)
                score = penaltyScore;
        }

        return score;
    }

    private static double computeTreeAlignmentSuboptimalScore(Set<Node> mainSet, Set<Node> iterationSet,
                                                              BiFunction<Path.Element, Path.Element, Double> penalty,
                                                              BiFunction<Set<Node>, Set<Node>, Double> getScores,
                                                              Map<Node, Node.Role> roles) {
//        double score = penalty.apply(getElement(mainNode, roles), null);
//        Set<Node> newMainSet;
        double localScore = Double.POSITIVE_INFINITY;
        Node matchedIterationNode = null;
        Set<Node> matchedIterationSet = null;
        Set<Node> matchedMainSet = null;
        Node matchedMainNode = null;
        for (Node mainNode : mainSet) {
            Set<Node> newMainSet = unionSet(getChildSet(mainNode), mainSet);
            newMainSet.remove(mainNode);
            double penaltyScore = penalty.apply(getElement(mainNode, roles), null);
            if (penaltyScore < localScore) {
                localScore = penaltyScore;
                matchedIterationNode = null;
                matchedIterationSet = iterationSet;
                matchedMainSet = newMainSet;
                matchedMainNode = mainNode;
            }
            for (Node iterationNode : iterationSet) {
                // PERFORMANCE
                newMainSet = new HashSet<>(mainSet);
                newMainSet.remove(mainNode);
                Set<Node> newIterationSet = new HashSet<>(iterationSet);
                newIterationSet.remove(iterationNode);
                penaltyScore =
                        penalty.apply(getElement(mainNode, roles), getElement(iterationNode, roles));
                if (penaltyScore < localScore) {
                    localScore = penaltyScore;
                    matchedIterationNode = iterationNode;
                    matchedIterationSet = newIterationSet;
                    matchedMainSet = newMainSet;
                    matchedMainNode = mainNode;
                }
            }

        }

        if (matchedIterationNode == null)
            return localScore + getScores.apply(matchedMainSet, matchedIterationSet);
        else
            return localScore
                    + getScores.apply(getChildSet(matchedMainNode), getChildSet(matchedIterationNode))
                    + getScores.apply(matchedMainSet, matchedIterationSet);
    }


    private static Path.Element getElement(Node node, Map<Node, Node.Role> roles) {
        if (node == null)
            return null;
        return new Path.Element(node, roles.get(node), new Node.Summary());
    }

    // things like this and getVarNodeSet should be in Snippet
    private static Set<Node> getChildSet(Node p) {
        return p.getChildren().stream().map(x -> x.node).collect(Collectors.toSet());
    }

    // things like this should be in a General.General class
    private static <T> Set<T> unionSet(Set<T> s1, Set<T> s2) {
        Set<T> res = new HashSet<>(s1);
        res.addAll(s2);
        return res;
    }

    private static <T> void divideValues(Map<T, Double> map, double val) {
        for (Map.Entry<T, Double> entry : map.entrySet())
            map.put(entry.getKey(), entry.getValue() / val);
    }

    private static Set<Value> getVarNodeSet(Snippet snippet) {
        Set<Value> result = new HashSet<>();
        NodeHelper.bfsOnNode(snippet.getRoot(), n -> {
            if (n instanceof Value && ((Value) n).getVariable() != null)
                result.add((Value) n);
            return true;
        });
        return result;
    }

    // PERFORMANCE
    private static void getPartPaths(Snippet snippet, Map<Node, Node.Role> roles, Map<Node, Path> result) {
        NodeHelper.bfsOnNode(snippet.getRoot(), n -> {
            Path path = new Path();

            Node currentNode = n.getParent();
            while (currentNode != null) {
                path.add(0, getElement(currentNode, roles));
                currentNode = currentNode.getParent();
            }
            result.put(n, path);
            return true;
        });
    }

}
