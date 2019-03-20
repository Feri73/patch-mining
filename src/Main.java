import New.AST.Node;
import New.AST.SnippetConverter;
import New.AST.SnippetConverter.Snippet;
import New.AST.Variable;
import New.ActGen.ActionGenerator;
import New.ActGen.ActionGenerator.Action;
import New.Patch;
import New.Program;
import Utils.BiMap;
import Utils.DefaultMap;
import Utils.Pair;
import Utils.StyledPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        testSameProramNodeMatching("10", "10p");

        why when i add the doodool function, it removes the append function?
        why in 10 vs 10p node matching the two ifs do not match?
        even if they (the two ifs) do not match, why it gives error when creating patch for 9?

//        testPatchGeneration("10", "10p", "9");
//        testSameProgramVariableMatching("2", "2p");
//        testActionGeneration("2", "2p");
    }
}
