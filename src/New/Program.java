package New;

import New.AST.Node;
import New.AST.Path;
import New.AST.SnippetConverter.Snippet;
import New.AST.Value;
import New.AST.Variable;
import Seq.SequenceDistanceCalculator;
import Seq.SequenceDistanceCalculator.Matching;
import Utils.Average;
import Utils.BiMap;
import Utils.Pair;

import java.util.*;
import java.util.stream.Collectors;

// in the paper, write that my algorithm makes sense because for two nodes to be matched, context+behavior should match
// and context in my case is the path from root to the node and behavior is the node itself (along with all its subtree)
// also note that my algorithm kinda is an extension of sequence alignment for tree alignment, AND the adjunct thing is
// a part of algorithm, not part of cost function (the things considered in adjuntc are algorithm parameters, just like
// the cost function itself, but the vert notion of using those adjunct is part of algorithm for ESTIMATING tree
// alignment, which is brute force essentially.

// test this
public class Program {

    // for both node and variable matching use th eiterative approach (test it at least)
    private final BiMap<Variable, Variable, Double> variableMatchScores;

    private Program() {
        variableMatchScores = null;
    }

    private Program(BiMap<Variable, Variable, Double> variableMatchScores) {
        if (variableMatchScores == null)
            this.variableMatchScores = null;
        else
            this.variableMatchScores = new BiMap<>(variableMatchScores);
    }

    private static boolean isVariable(Node node) {
        return node instanceof Value && ((Value) node).getVariable() != null;
    }

    // instead of this, I should have a Variable class that represents variables (instead of String) and then get the variable (along with its name, matched variables, and ...)
    private static Path.Element pathLast(Path path) {
        return path.get(path.size() - 1);
    }

    private BiMap<Path, Path, Matching<Path.Element>>
    getPath1Path2Matchings(Collection<Path> paths1, Collection<Path> paths2) {
        BiMap<Path, Path, SequenceDistanceCalculator.Matching<Path.Element>> result = new BiMap<>();

        for (Path path1 : paths1)
            for (Path path2 : paths2)
                result.put(path1, path2, getSequenceMatching(path1, path2));

        return result;
    }

    // PERFORMANCE
    // this should not be greedy
    public static <T> List<Pair<T, T>> getGreedyMatches(BiMap<T, T, Double> t1T2Scores) {
        Map<T, T> matches = new HashMap<>();

        while (true) {
            double currentMin = Double.POSITIVE_INFINITY;
            Pair<T, T> currentMatching = null;

            for (BiMap.Entry<T, T, Double> t1T2Score : t1T2Scores.getEntries())
                // PERFORMANCE: CONTAINS_VALUE
                if (!matches.containsKey(t1T2Score.getKey1()) && !matches.containsValue(t1T2Score.getKey2())
                        && t1T2Score.getValue() < currentMin) {
                    currentMin = t1T2Score.getValue();
                    currentMatching = new Pair<>(t1T2Score.getKey1(), t1T2Score.getKey2());
                }

            if (currentMatching == null)
                break;
            matches.put(currentMatching.getFirst(), currentMatching.getSecond());
        }

        return matches.entrySet().stream().map(x -> new Pair<>(x.getKey(), x.getValue())).collect(Collectors.toList());
    }

    public static <T> Map<T, T> getMatchMap(List<Pair<T, T>> matches) {
        return matches.stream().collect(() -> new HashMap<T, T>(), (a, b) -> {
            a.put(b.getFirst(), b.getSecond());
            a.put(b.getSecond(), b.getFirst());
        }, (a, b) -> a.putAll(b));
    }

    private BiMap<Path, Path, Double>
    getPartPath1Path2Scores(List<Path> paths1, List<Path> paths2) {
        BiMap<Path, Path, Double> result = new BiMap<>();

        // to force that the two variables match, I exclude the last
        Map<Path, Path> partPaths1 = paths1.stream()
                .collect(Collectors.toMap(x -> new Path(x.subList(0, x.size() - 1)), x -> x));
        Map<Path, Path> partPaths2 = paths2.stream()
                .collect(Collectors.toMap(x -> new Path(x.subList(0, x.size() - 1)), x -> x));

        BiMap<Path, Path, Matching<Path.Element>> partPath1Path2Matchings =
                getPath1Path2Matchings(partPaths1.keySet(), partPaths2.keySet());
        for (BiMap.Entry<Path, Path, Matching<Path.Element>> partPath1Path2Matching
                : partPath1Path2Matchings.getEntries()) {
            Path path1 = partPaths1.get(partPath1Path2Matching.getKey1());
            Path path2 = partPaths2.get(partPath1Path2Matching.getKey2());

            Path.Element var1 = pathLast(path1);
            Path.Element var2 = pathLast(path2);

            if (isVariable(var1.node) && isVariable(var2.node))
                result.put(path1, path2,
                        combineMatchingScores(partPath1Path2Matching.getValue().score, getPenalty(var1, var2)));
        }

        return result;
    }

    private static BiMap<Path, Variable, Double>
    getPath1Var2Scores(BiMap<Path, Path, Double> partPath1Path2Scores) {
        BiMap<Path, Variable, Double> result = new BiMap<>((p, s) -> Double.POSITIVE_INFINITY);

        for (BiMap.Entry<Path, Path, Double> partPath1Path2Score : partPath1Path2Scores.getEntries()) {
            Path path1 = partPath1Path2Score.getKey1();
            Path path2 = partPath1Path2Score.getKey2();
            double score = partPath1Path2Score.getValue();

            Variable var2 = ((Value) pathLast(path2).node).getVariable();

            if (score < result.get(path1, var2))
                result.put(path1, var2, score);
        }

        return result;
    }

    private static BiMap<Variable, Variable, Average>
    getPathVar1Var2Scores(BiMap<Path, Variable, Double> path1Var2Scores, Set<Variable> var2Set) {
        BiMap<Variable, Variable, Average> result = new BiMap<>((a, b) -> new Average());

        for (Path path1 : path1Var2Scores.getKeys1())
            for (Variable var2 : var2Set) {
                Variable var1 = ((Value) pathLast(path1).node).getVariable();
                double score = path1Var2Scores.get(path1, var2);

                result.get(var1, var2).selfAdd(new Average(score, 1));
            }

        return result;
    }

    private static BiMap<Variable, Variable, Double>
    getVar1Var2Scores(BiMap<Variable, Variable, Average> pathVar1Var2Scores,
                      BiMap<Variable, Variable, Average> pathVar2Var1Scores) {
        BiMap<Variable, Variable, Double> result = new BiMap<>();

        for (Variable var1 : pathVar1Var2Scores.getKeys1())
            for (Variable var2 : pathVar2Var1Scores.getKeys1()) {
                Double score = pathVar1Var2Scores.get(var1, var2).add(pathVar2Var1Scores.get(var2, var1)).getValue();
                if (!score.isNaN())
                    result.put(var1, var2, score);
            }

        return result;
    }

    public static BiMap<Variable, Variable, Double>
    getVariableMatcheScores(Snippet snippetP, Snippet snippetQ, BiMap<Variable, Variable, Double> varMatchScores) {
        // PERFORMANCE (FOR MATCHING NODES I ALSO USE THESE PATHS)
        List<Path> pathsP = snippetP.getRoot().getPaths(Node.Role.Root);
        List<Path> pathsQ = snippetQ.getRoot().getPaths(Node.Role.Root);

        BiMap<Path, Path, Double> partPathPPathQScores =
                new Program(varMatchScores).getPartPath1Path2Scores(pathsP, pathsQ);

        BiMap<Path, Variable, Double> pathPVarQScores = getPath1Var2Scores(partPathPPathQScores);
        BiMap<Path, Variable, Double> pathQVarPScores = getPath1Var2Scores(partPathPPathQScores.getSwapped());

        BiMap<Variable, Variable, Average> pathVarPVarQScores =
                getPathVar1Var2Scores(pathPVarQScores, snippetQ.getVariables());
        BiMap<Variable, Variable, Average> pathVarQVarPScores =
                getPathVar1Var2Scores(pathQVarPScores, snippetP.getVariables());

        return getVar1Var2Scores(pathVarPVarQScores, pathVarQVarPScores);
    }

    private static BiMap<Node, Node, Average> getMatchingNode1Node2Scores(Matching<Path.Element> matching) {
        BiMap<Node, Node, Average> result = new BiMap<>();

        double score = 1.0 / matching.score;

        for (Pair<Path.Element, Path.Element> match : matching.matching) {
            if (match.getFirst() == null)
                continue;
            Node node1 = match.getFirst().node;

            for (Pair<Path.Element, Path.Element> tmpMatch : matching.matching) {
                if (match == tmpMatch || tmpMatch.getSecond() == null)
                    continue;
                Node node2P = tmpMatch.getSecond().node;
                result.put(node1, node2P, new Average(0, 1 + score));
            }

            if (match.getSecond() == null)
                continue;

            Node node2 = match.getSecond().node;
            result.put(node1, node2, new Average(score * score, 1 + score));
        }

        return result;
    }

    private BiMap<Node, Node, Average> getNode1Node1Scores(List<Path> paths1, List<Path> paths2) {
        BiMap<Node, Node, Average> result = new BiMap<>((a, b) -> new Average());

        BiMap<Path, Path, Matching<Path.Element>> path1Path1Matchings = getPath1Path2Matchings(paths1, paths2);

        for (BiMap.Entry<Path, Path, Matching<Path.Element>> path1Path1Matching : path1Path1Matchings.getEntries())
            for (BiMap.Entry<Node, Node, Average> matchingNode1Node2Score :
                    getMatchingNode1Node2Scores(path1Path1Matching.getValue()).getEntries())
                result.get(matchingNode1Node2Score.getKey1(), matchingNode1Node2Score.getKey2())
                        .selfAdd(matchingNode1Node2Score.getValue());

        return result;
    }

    public static BiMap<Node, Node, Double>
    getNodeMatchScores(Snippet snippetP, Snippet snippetQ, BiMap<Variable, Variable, Double> variableMatchScores) {
        return new Program(variableMatchScores)
                .getNode1Node1Scores(snippetP.getRoot().getPaths(Node.Role.Root),
                        snippetQ.getRoot().getPaths(Node.Role.Root))
                .getEntries().stream()
                .collect(() -> new BiMap<>(), (a, b) -> a.put(b.getKey1(), b.getKey2(),
                        1.0 / b.getValue().getValue()), (a, b) -> a.putAll(b));
    }

    public static List<Pair<Node, Node>> getNodeMatches(Snippet snippetP, Snippet snippetQ) {
        return getGreedyMatches(getNodeMatchScores(snippetP, snippetQ,
                getVariableMatcheScores(snippetP, snippetQ, null)));
    }

    private double getPenalty(Path.Element element1, Path.Element element2) {
        double penalty = new Penalty(variableMatchScores).getPenalty(element1, element2);
        if (penalty == 1.0)
            penalty -= .0001;
        return -Math.log(penalty);
    }

    // two asusmptions about the score the returned in matching:
    // 1. less is better
    // 2. the score is in (0,inf)
    // the assumotion that lets me use sequence matching: if node n1 and n2 are each other's true match, no ascendent of
    // one can be true match of descendent of the other.
    // matching score is actually matching confidence (asuming penalty function (not the one here) shows the confidence
    // that two nodes are actual matches
    private Matching<Path.Element> getSequenceMatching(Path path1, Path path2) {
        SequenceDistanceCalculator.Matching<Path.Element> matching =
                SequenceDistanceCalculator.calculate(path1, path2, this::getPenalty);
        // is 1/l the best option i have (e.g. think about why we do not average the sequence elements alignment scores and instead add them? does it mean that we do not need length normalization at all here?). also shouldn't i add 1/l in sequence alignment algorithm instead of here?
        matching.score /= path1.size() + path2.size();
        return matching;
    }

    private static double combineMatchingScores(double score1, double score2) {
        return score1 + score2;
    }
}
