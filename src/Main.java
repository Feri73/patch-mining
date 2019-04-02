import New.AST.*;
import New.AST.SnippetConverter.Snippet;
import New.ActGen.ActionGenerator;
import New.ActGen.ActionGenerator.Action;
import New.Patch;
import New.Penalty;
import New.Program;
import Utils.BiMap;
import Utils.DefaultMap;
import Utils.Pair;
import Utils.StyledPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Main {
    private static String getNodeTextual(Node node, Function<Node.Text, String> converter) {
        return node.toTextual("").stream()
                .reduce("", (a, b) -> a += converter.apply(b), (a, b) -> a += b);
    }

    private static Snippet readSnippet(String name) throws FileNotFoundException {
        return new SnippetConverter().convertToSnippet(new File(name + ".java"), "main");
    }

    private static void printStyledSnippets(Snippet snippet1, Snippet snippet2, List<Pair<Node, Node>> nodeMatches) {
        Map<Node, String> nodeStyleMap = new DefaultMap<>(a -> "");

        int styleIndex = 4;
        Field[] styleFields = Arrays.stream(StyledPrinter.class.getFields())
                .sorted(Comparator.comparing(a -> new StringBuilder(a.getName()).reverse())).toArray(s -> new Field[s]);
        for (Pair<Node, Node> nodeMatch : nodeMatches.stream()
                .sorted(Comparator.comparing(
                        x -> getNodeTextual(x.getFirst(), a -> a.value) + getNodeTextual(x.getSecond(), a -> a.value)))
                .collect(Collectors.toList()))
            try {
                styleIndex %= styleFields.length;
                while ("RESET".equals(styleFields[styleIndex].getName())
                        || styleFields[styleIndex].getName().contains("WHITE")
                        || styleFields[styleIndex].getName().contains("BLACK"))
                    styleIndex = (styleIndex + 1) % styleFields.length;
                String style = (String) styleFields[styleIndex].get(null);

                nodeStyleMap.put(nodeMatch.getFirst(), style);
                nodeStyleMap.put(nodeMatch.getSecond(), style);
                styleIndex++;
            } catch (IllegalAccessException e) {
                throw new RuntimeException();
            }

        System.out.println(getNodeTextual(snippet1.getRoot(),
                a -> StyledPrinter.applyStyle(nodeStyleMap.get(a.generatingNode), a.value)));
        System.out.println();
        System.out.println(getNodeTextual(snippet2.getRoot(),
                a -> StyledPrinter.applyStyle(nodeStyleMap.get(a.generatingNode), a.value)));
    }

    private static void testPatchGeneration(String nBef, String nAft, String nNew) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);
        Snippet snippetNew = readSnippet(nNew);

        Node patched = Patch.subtract(snippetBef, snippetAft).add(snippetNew);
        System.out.println(getNodeTextual(patched, x -> x.value));
    }

    private static void testSameProramNodeMatching(String nBef, String nAft) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);

        BiMap<Variable, Variable, Double> varMatchScores =
                Patch.getVariableMatchScoresBetweenVersions(snippetBef, snippetAft);
        List<Pair<Node, Node>> nodeMatches =
                Program.getGreedyMatches(Program.getNodeMatchScores(snippetBef, snippetAft, varMatchScores));
        printStyledSnippets(snippetBef, snippetAft, nodeMatches);
    }

    private static void testNodeMatching(String nP, String nQ) throws FileNotFoundException {
        Snippet snippet1 = readSnippet(nP);
        Snippet snippet2 = readSnippet(nQ);

        List<Pair<Node, Node>> nodeMatches = Program.getNodeMatches(snippet1, snippet2);
        printStyledSnippets(snippet1, snippet2, nodeMatches);
    }

    private static void testSameProgramVariableMatching(String nBef, String nAft) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);

        List<Pair<Variable, Variable>> variableMatches =
                Program.getGreedyMatches(Patch.getVariableMatchScoresBetweenVersions(snippetBef, snippetAft));
        for (Pair<Variable, Variable> variableMatch : variableMatches)
            System.out.println(variableMatch.getFirst().getName() + "<==>" + variableMatch.getSecond().getName());
    }

    private static void testVariableMatching(String nP, String nQ) throws FileNotFoundException {
        Snippet snippet1 = readSnippet(nP);
        Snippet snippet2 = readSnippet(nQ);

        List<Pair<Variable, Variable>> variableMatches =
                Program.getGreedyMatches(Program.getVariableMatcheScores(snippet1, snippet2, null));
        for (Pair<Variable, Variable> variableMatch : variableMatches)
            System.out.println(variableMatch.getFirst().getName() + "<==>" + variableMatch.getSecond().getName());
    }

    private static void testActionGeneration(String nBef, String nAft) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);

        BiMap<Variable, Variable, Double> varMatchScores =
                Patch.getVariableMatchScoresBetweenVersions(snippetBef, snippetAft);
        List<Action> actions = ActionGenerator.generate(snippetBef, snippetAft,
                Program.getGreedyMatches(Program.getNodeMatchScores(snippetBef, snippetAft, varMatchScores)));
    }

    public static void main(String[] args) throws Exception {
//        testVariableMatching("6", "5");
//        testNodeMatching("10", "9");
//        testSameProramNodeMatching("10", "10p");

//        why when i add the doodool function, it removes the append function?
//        why in 10 vs 10p node matching the two ifs do not match?
//        even if they (the two ifs) do not match, why it gives error when creating patch for 9?

//        testPatchGeneration("10", "10p", "9");
//        testSameProgramVariableMatching("2", "2p");
//        testActionGeneration("2", "2p");

        temp_Iterative(readSnippet("17"), readSnippet("18"));
    }


    // it is important to node: in this algorithm, (if i use an algorithm to convert m Map to a node matching (like the
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
    private static void temp_Iterative(Snippet snippetP, Snippet snippetQ) {
        Map<Node, Node.Role> roles = new DefaultMap<>(n ->
                n.getParent() == null ? Node.Role.Root : n.getParent().getChild(n).role);
        BiMap<Node, Node, Double> m = new BiMap<>((a, b) -> 1.0);
        // incorporate vairbale matching in l
        // generally use other ideas to create a better l
        BiMap<Node, Node, Double> l = new BiMap<>((a, b) -> a.getClass().equals(b.getClass()) ?
                a instanceof Value && b instanceof Value && (((Value) a).getVariable() == null) !=
                        (((Value) b).getVariable() == null) ? 0.0 : 1.0 : 0.0);
//        BiMap<Node, Node, Double> l = new BiMap<>((a, b) ->
//                Penalty.classPenaltyFunctionMap
//                        .get(new Penalty.Element(temp_getElement(a, roles), temp_getElement(b, roles))));
//         it should be r1==r2 ? 1 : 0 but for now i made l to represent role as well (via penalty class)
        BiMap<Node.Role, Node.Role, Double> r = new BiMap<>((r1, r2) -> r1 == r2 ? 1.0 : 0.0);

        // nullVal cannot be more than .5
        // find a better approach for nullValue than this (one idea is to have specific nullValue for each class (like how I had in the preivous appoach)
        double nullValue = .5;
        double nullValueStp = .07;
        double nullValueMin = .1;

        Map<Node, Path> partPaths = new HashMap<>();
        temp_partPath(snippetP, roles, partPaths);
        temp_partPath(snippetQ, roles, partPaths);

        // instead of n, compare m and stop when it converges
        int n = 10;
        while (n-- > 0) {
            // if i add any parameter to this method, i should consider dividing it to toDivide (if applicale)
            m = temp_IterativeIteration(snippetP.getRoot(), snippetQ.getRoot(),
                    // for learning par and chil, one good way to speed the learning up is to assume they are equal
                    m, l, r, partPaths, roles, 2, 3, 3,
                    temp_getVarSet(snippetP), temp_getVarSet(snippetQ), nullValue);

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
        BiMap<Node, Node, Double> finalM = m;
        for (BiMap.Entry<Node, Node, Double> entry : m.getEntries())
            finalM.put(entry.getKey1(), entry.getKey2(), -entry.getValue());
        printStyledSnippets(snippetP, snippetQ, Program.getGreedyMatches(finalM));

    }

    private static <T> void divideValues(Map<T, Double> map, double val) {
        for (Map.Entry<T, Double> entry : map.entrySet())
            map.put(entry.getKey(), entry.getValue() / val);
    }

    private static Set<Value> temp_getVarSet(Snippet snippet) {
        Set<Value> result = new HashSet<>();
        temp_bfsOnNode(snippet.getRoot(), n -> {
            if (n instanceof Value && ((Value) n).getVariable() != null)
                result.add((Value) n);
            return true;
        });
        return result;
    }

    // PERFORMANCE
    private static void temp_partPath(Snippet snippet, Map<Node, Node.Role> roles, Map<Node, Path> partPaths) {
        temp_bfsOnNode(snippet.getRoot(), n -> {
            Path path = new Path();

            Node currentNode = n.getParent();
            while (currentNode != null) {
                path.add(0, temp_getElement(currentNode, roles));
                currentNode = currentNode.getParent();
            }
            partPaths.put(n, path);
            return true;
        });
    }


    // i need this to improve performance significantly (the reason i do not use the same technique for par is that
    // that does not take that much. BUT IT CERTAINLY HELPS IN THAT CASE TOO (PERFORMANCE))
    private static BiMap<Set<Node>, Set<Node>, Double> temp_treeAlignmentScores;

    private static BiMap<Node, Node, Double> temp_nodeSubtreeMin;

    private static BiMap<Node, Node, Double> temp_seqAlignmentScores;

    private static BiMap<Node, Node, Double>
    temp_IterativeIteration(Node rootP, Node rootQ, BiMap<Node, Node, Double> m,
                            BiMap<Node, Node, Double> l, BiMap<Node.Role, Node.Role, Double> r,
                            Map<Node, Path> partPaths, Map<Node, Node.Role> roles,
                            double par, double chil, double varMatch,
                            // varP and varQ contain only Value nodes that are var or input, not literals
                            Set<Value> varsP, Set<Value> varsQ,
                            // this nullVal can actually be a map like m that can be learned (think about how what
                            //      happens that causes a node to match nothing)
                            Double nullVal) {
        temp_treeAlignmentScores = null;
        temp_nodeSubtreeMin = null;
        temp_seqAlignmentScores = null;

        BiMap<Node, Node, Double> result = new BiMap<>();
        temp_bfsOnNode(rootP, nP -> {
            // i can use hueristics to avoid doing this for all pairs of nP&nQ (in penalty class, what pairs are zero?)
            temp_bfsOnNode(rootQ, nQ -> {
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
                    parScore = -temp_pathAlignmentHelper(nP, nQ, partPaths,
                            (eP, eQ) -> {
                                if (eP == null || eQ == null)
                                    // maybe the nullVal map for parents should be different to that of the children
                                    return -Penalty.classPenaltyFunctionMap.get(new Penalty.Element(eP, eQ)) / 2;
//                                    return -nullVal;
                                // i removed r from here cuz i add it in local score
                                return -m.get(eP.node, eQ.node);
                            }, roles) / parN;


//                double chilScore = -temp_simpleNodeMatchFinderHelper(nP, nQ, (eP, eQ) -> {
//                    if (eP == null || eQ == null)
//                        return -nullVal;
//                    return -chil.apply(m.get(eP.node, eQ.node) + r.get(eP.role, eQ.role));
//                }, roles);

                int chilMaxSize = Math.max(temp_subtreeSize.get(nP) - 1, temp_subtreeSize.get(nQ) - 1);
                int chilMinSize = Math.min(temp_subtreeSize.get(nP) - 1, temp_subtreeSize.get(nQ) - 1);
                double chilN = (chilMaxSize + chilMinSize) / 2.0;// chilMinSize + nullVal * (chilMaxSize - chilMinSize);
                double chilScore;
                if (chilN == 0)
                    chilScore = 1;
                else
                    chilScore = -temp_partTreeAlignmentHelper(nP, nQ,
                            (eP, eQ) -> {
                                if (eP == null || eQ == null)
                                    return -Penalty.classPenaltyFunctionMap.get(new Penalty.Element(eP, eQ)) / 2;
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

//                System.out.println(temp_treeAlignmentScores.size());

                return true;
            });
            return true;
        });

        return result;
    }

    private static final Map<Node, Integer> temp_subtreeSize = new DefaultMap<>(
            node -> {
                int size = 1;
                for (Node.Child c : node.getChildren())
                    size += Main.temp_subtreeSize.get(c.node);
                return size;
            });

    private static void temp_bfsOnNode(Node node, Function<Node, Boolean> function) {
        ArrayDeque<Node> nodeQueue = new ArrayDeque<>();
        nodeQueue.push(node);
        while (!nodeQueue.isEmpty()) {
            Node next = nodeQueue.pop();
            if (function.apply(next))
                for (Node.Child child : next.getChildren())
                    nodeQueue.push(child.node);
        }
    }

    private static double temp_pathAlignmentHelper(Node nP, Node nQ, Map<Node, Path> partPaths,
                                                   BiFunction<Path.Element, Path.Element, Double> penalty,
                                                   Map<Node, Node.Role> roles) {
        if (temp_seqAlignmentScores == null)
            temp_seqAlignmentScores = new BiMap<>(
                    (n1, n2) -> temp_pathAlignment(n1, n2, partPaths, penalty, roles, temp_seqAlignmentScores));
        return temp_seqAlignmentScores.get(nP.getParent(), nQ.getParent());
    }

    private static double temp_pathAlignment(Node nP, Node nQ, Map<Node, Path> partPaths,
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
                    penalty.apply(temp_getElement(nP, roles), temp_getElement(nQ, roles))
                            + memeoise.get(nPNext, nQNext));
        if (nP != null)
            score = Math.min(score,
                    penalty.apply(temp_getElement(nP, roles), null) + memeoise.get(nPNext, nQ));
        if (nQ != null)
            score = Math.min(score,
                    penalty.apply(null, temp_getElement(nQ, roles)) + memeoise.get(nP, nQNext));
        return score;

    }

    private static Path.Element temp_getElement(Node node, Map<Node, Node.Role> roles) {
        if (node == null)
            return null;
        return new Path.Element(node, roles.get(node), new Node.Summary());
    }

    // tries to minimize score
    // maybe a similar algorithm for parents (instead of sequence alignment) is better??
    // test if this is correctly implemented
    private static double temp_simpleNodeMatchFinderHelper(Node rP, Node rQ,
                                                           BiFunction<Path.Element, Path.Element, Double> penalty,
                                                           Map<Node, Node.Role> roles,
                                                           double nullVal) {
        // bad practice
        var penaltyContext = new Object() {
            BiFunction<Path.Element, Path.Element, Double> usedPenalty;
        };

        if (temp_nodeSubtreeMin == null)
            temp_nodeSubtreeMin = new BiMap<>(
                    (n, s) -> temp_simpleNodeMatchFinder(n, s, penaltyContext.usedPenalty,
                            roles, temp_nodeSubtreeMin, nullVal));

        Map<Node, Double> nodeBestScores = new DefaultMap<>(x -> Double.POSITIVE_INFINITY);

        penaltyContext.usedPenalty = penalty;
        temp_bfsOnNode(rP, nP -> {
            if (nP == rP)
                return true;
            double newScore = rQ.getChildren().stream()
                    .map(x -> temp_nodeSubtreeMin.get(nP, x.node)).mapToDouble(x -> x).min().orElse(nullVal);
            if (newScore < nodeBestScores.get(nP))
                nodeBestScores.put(nP, newScore);
            return true;
        });

        penaltyContext.usedPenalty = (a, b) -> penalty.apply(b, a);
        temp_bfsOnNode(rQ, nQ -> {
            if (nQ == rQ)
                return true;
            double newScore = rP.getChildren().stream()
                    .map(x -> temp_nodeSubtreeMin.get(nQ, x.node)).mapToDouble(x -> x).min().orElse(nullVal);
            if (newScore < nodeBestScores.get(nQ))
                nodeBestScores.put(nQ, newScore);
            return true;
        });

        return nodeBestScores.entrySet().stream().mapToDouble(x -> x.getValue()).sum();
    }

    private static double temp_simpleNodeMatchFinder(Node node, Node subtree,
                                                     BiFunction<Path.Element, Path.Element, Double> penalty,
                                                     Map<Node, Node.Role> roles,
                                                     BiMap<Node, Node, Double> memoise,
                                                     double nullValue) {
        double score1 = Math.min(nullValue,
                penalty.apply(temp_getElement(node, roles), temp_getElement(subtree, roles)));
        Optional<Double>
                score2 = subtree.getChildren().stream().map(x -> memoise.get(node, x.node)).min(Double::compareTo);
        return score2.map(aDouble -> Math.min(score1, aDouble)).orElse(score1);
    }

    private static double temp_partTreeAlignmentHelper(Node rP, Node rQ,
                                                       BiFunction<Path.Element, Path.Element, Double> penalty,
                                                       Map<Node, Node.Role> roles) {
        // make sure that set is compared regarding its content not its reference
        if (temp_treeAlignmentScores == null)
            temp_treeAlignmentScores = new BiMap<>((s1, s2) -> temp_treeAlignment(s1, s2,
                    penalty, temp_treeAlignmentScores, roles));
        // because part
        return temp_treeAlignmentScores.get(
                new HashSet<>(rP.getChildren().stream().map(x -> x.node).collect(Collectors.toList())),
                new HashSet<>(rQ.getChildren().stream().map(x -> x.node).collect(Collectors.toList())));
    }

    // one way to test it is to compare it to the sequence matcher (if trees are sequences, the two should be the same)
    // make sure this algorithm (without optimization heuristics) is optimum (explores all possible combinations)
    // i can use heuristics to avoid exploring all possiblities
    // like seq alignment, this tries to minimize the score
    // one way to estimate this is gumtreediff (or things like that)
    private static double temp_treeAlignment(Set<Node> sP, Set<Node> sQ,
                                             BiFunction<Path.Element, Path.Element, Double> penalty,
                                             BiMap<Set<Node>, Set<Node>, Double> scores,
                                             Map<Node, Node.Role> roles) {
        double score = Double.POSITIVE_INFINITY;
        if (sP.isEmpty() && sQ.isEmpty())
            return 0;
        if (!sP.isEmpty())
            score = temp_computeSuboptimumScore(sP, sQ, penalty, (a, b) -> scores.get(a, b), roles);
        if (!sQ.isEmpty()) {
            double newScore = temp_computeSuboptimumScore(sQ, sP,
                    (a, b) -> penalty.apply(b, a), (a, b) -> scores.get(b, a), roles);
            if (newScore < score)
                score = newScore;
        }
        return score;
    }

    private static double temp_computeScore(Set<Node> mainSet, Set<Node> iterationSet,
                                            BiFunction<Path.Element, Path.Element, Double> penalty,
                                            BiFunction<Set<Node>, Set<Node>, Double> getScores,
                                            Map<Node, Node.Role> roles) {
        Node mainNode = mainSet.iterator().next();
        Set<Node> newMainSet = temp_unionSet(temp_getChildSet(mainNode), mainSet);
        newMainSet.remove(mainNode);
        double score = penalty.apply(temp_getElement(mainNode, roles), null)
                + getScores.apply(newMainSet, iterationSet);

        for (Node iterationNode : iterationSet) {
            newMainSet = new HashSet<>(mainSet);
            newMainSet.remove(mainNode);
            Set<Node> newIterationSet = new HashSet<>(iterationSet);
            newIterationSet.remove(iterationNode);
            double penaltyScore =
                    penalty.apply(temp_getElement(mainNode, roles), temp_getElement(iterationNode, roles));
            // this if makes this suboptimal
//            if (mainNode.getClass() != iterationNode.getClass() && roles.get(mainNode) != roles.get(iterationNode))
//                continue;
            penaltyScore += getScores.apply(temp_getChildSet(mainNode), temp_getChildSet(iterationNode))
                    + getScores.apply(newMainSet, newIterationSet);
            if (penaltyScore < score)
                score = penaltyScore;
        }

        return score;
    }

    private static double temp_computeSuboptimumScore(Set<Node> mainSet, Set<Node> iterationSet,
                                                      BiFunction<Path.Element, Path.Element, Double> penalty,
                                                      BiFunction<Set<Node>, Set<Node>, Double> getScores,
                                                      Map<Node, Node.Role> roles) {
//        double score = penalty.apply(temp_getElement(mainNode, roles), null);
//        Set<Node> newMainSet;
        double localScore = Double.POSITIVE_INFINITY;
        Node matchedIterationNode = null;
        Set<Node> matchedIterationSet = null;
        Set<Node> matchedMainSet = null;
        Node matchedMainNode = null;
        for (Node mainNode : mainSet) {
            Set<Node> newMainSet = temp_unionSet(temp_getChildSet(mainNode), mainSet);
            newMainSet.remove(mainNode);
            double penaltyScore = penalty.apply(temp_getElement(mainNode, roles), null);
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
                        penalty.apply(temp_getElement(mainNode, roles), temp_getElement(iterationNode, roles));
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
                    + getScores.apply(temp_getChildSet(matchedMainNode), temp_getChildSet(matchedIterationNode))
                    + getScores.apply(matchedMainSet, matchedIterationSet);
    }


    private static Set<Node> temp_getChildSet(Node p) {
        return p.getChildren().stream().map(x -> x.node).collect(Collectors.toSet());
    }

    private static <T> Set<T> temp_unionSet(Set<T> s1, Set<T> s2) {
        Set<T> res = new HashSet<>(s1);
        res.addAll(s2);
        return res;
    }

    private static List<Node> getNodesBy(Node root, String text) {
        List<Node> result = new ArrayList<>();
        temp_bfsOnNode(root,
                n -> {
                    if (n instanceof Value && ((Value) n).getText().equals(text))
                        result.add(n);
                    if (n.getClass().getName().contains('.' + text))
                        result.add(n);
                    return true;
                });
        return result;
    }
}
