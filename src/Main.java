import New.AST.*;
import New.AST.SnippetConverter.Snippet;
import New.ActGen.ActionGenerator;
import New.ActGen.ActionGenerator.Action;
import New.NodeMatcher;
import New.Patch;
import Utils.*;
import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Main {
    private static String getNodeTextual(Node node, Function<Node.Text, String> converter, String prefix) {
        return node.toTextual(prefix).stream()
                .reduce("", (a, b) -> a += converter.apply(b), (a, b) -> a += b);
    }

    private static Snippet readSnippet(String name) throws FileNotFoundException {
        return new SnippetConverter().convertToSnippet(new File(name + ".java"), "main");
    }

    // this should be private
    public static void printStyledSnippets(Snippet snippet1, Snippet snippet2, List<Pair<Node, Node>> nodeMatches) {
        Map<Node, String> nodeStyleMap = new DefaultMap<>(a -> "");

        int styleIndex = 4;
        Field[] styleFields = Arrays.stream(StyledPrinter.class.getFields())
                .sorted(Comparator.comparing(a -> new StringBuilder(a.getName()).reverse())).toArray(s -> new Field[s]);
        for (Pair<Node, Node> nodeMatch : nodeMatches.stream()
                .sorted(Comparator.comparing(
                        x -> getNodeTextual(x.getFirst(), a -> a.value, "") +
                                getNodeTextual(x.getSecond(), a -> a.value, "")))
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
                a -> StyledPrinter.applyStyle(nodeStyleMap.get(a.generatingNode), a.value), ""));
        System.out.println();
        System.out.println(getNodeTextual(snippet2.getRoot(),
                a -> StyledPrinter.applyStyle(nodeStyleMap.get(a.generatingNode), a.value), ""));
    }

    public static String toCompilableProgram(Node node) {
        // what if a local var i final e.g.?
        // what if two local vars with same name are defined (in real code) in two different scopes? now i handle this
        // by giving them diferent name. but the best option to do is to define each variable in its correct scope
        Set<Variable> localVars = new HashSet<>();

        NodeHelper.bfsOnNode(node, n -> {
            if (n instanceof Value && ((Value) n).getVariable() != null
                    && ((Value) n).getVariable().getKind() == Variable.Kind.Local)
                localVars.add(((Value) n).getVariable());
            return true;
        });

        String res = "";

        res += localVars.stream().map(v -> "\t\t" + v.getType().toString() + ' ' + v.getName() + ";\n")
                .collect(Collectors.joining());
        // this assumes all functions are intended once
        String texual = getNodeTextual(node, x -> x.value, "\t");
        res += texual.substring(1, texual.length() - 2);

        return res;
    }

    public static String replaceInFile(Node newBody, String file, int statementsStartLine, int statementsEndLine) throws FileNotFoundException {
        StringBuilder result = new StringBuilder();

        boolean addedChanges = false;

        Scanner scan = new Scanner(new File(file));
        scan.useDelimiter("\n");
        int lineNumber = 0;
        while (scan.hasNext()) {
            lineNumber++;
            String line = scan.next();
            if (lineNumber < statementsStartLine || lineNumber > statementsEndLine)
                result.append(line).append('\n');
            else if (!addedChanges) {
                result.append(toCompilableProgram(newBody) + '\n');
                addedChanges = true;
            }
        }
        scan.close();

        return result.toString();
    }

    private static void testPatchGenerationToFile(String nBef, String nAft, String nNew) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);

        Scanner scan = new Scanner(new File(nNew + ".java"));
        scan.useDelimiter("\\Z");
        String content = scan.next();
        scan.close();
        CtClass<?> clazz = Launcher.parseClass(content);
        CtBlock<?> body = clazz.getMethodsByName("main").get(0).getBody();

        Node patched = Patch.subtract(snippetBef, snippetAft).add(new SnippetConverter().convertToSnippet(body));

        // this assumes the opening bracket of methods is in the declaring line and statements start the next line
        // and also the ending bracket is in the line after the last statement
        System.out.println(replaceInFile(patched, nNew + ".java",
                body.getPosition().getLine() + 1, body.getPosition().getEndLine() - 1));
    }

    private static void testPatchGeneration(String nBef, String nAft, String nNew) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);
        Snippet snippetNew = readSnippet(nNew);

        Node patched = Patch.subtract(snippetBef, snippetAft).add(snippetNew);
        System.out.println(getNodeTextual(patched, x -> x.value, ""));
    }

    private static void testSameProramNodeMatching(String nBef, String nAft) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);

        Patch patch = new Patch(snippetBef, snippetAft);

        List<Pair<Node, Node>> nodeMatches =
                General.getGreedyMatches(patch.befAftMatcher.getNodeMatchScores());
        printStyledSnippets(snippetBef, snippetAft, nodeMatches);
    }

    private static void testNodeMatching(String nP, String nQ) throws FileNotFoundException {
        Snippet snippet1 = readSnippet(nP);
        Snippet snippet2 = readSnippet(nQ);

        List<Pair<Node, Node>> nodeMatches = General.getGreedyMatches(
                new NodeMatcher(snippet1, snippet2, null).getNodeMatchScores());
        printStyledSnippets(snippet1, snippet2, nodeMatches);
    }

    private static void testIterativeNodeMatching(Snippet snippetP, Snippet snippetQ) {
        printStyledSnippets(snippetP, snippetQ, General.getGreedyMatches(
                new NodeMatcher(snippetP, snippetQ, null).getNodeMatchScores()));
    }

    private static void testSameProgramVariableMatching(String nBef, String nAft) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);

        Patch patch = new Patch(snippetBef, snippetAft);

        List<Pair<Variable, Variable>> variableMatches =
                General.getGreedyMatches(NodeMatcher
                        .computeVariableMatchScores(patch.befAftMatcher.getNodeMatchScores()));
        for (Pair<Variable, Variable> variableMatch : variableMatches)
            System.out.println(variableMatch.getFirst().getName() + "<==>" + variableMatch.getSecond().getName());
    }

    private static void testVariableMatching(String nP, String nQ) throws FileNotFoundException {
        Snippet snippet1 = readSnippet(nP);
        Snippet snippet2 = readSnippet(nQ);

        List<Pair<Variable, Variable>> variableMatches =
                General.getGreedyMatches(NodeMatcher
                        .computeVariableMatchScores(new NodeMatcher(snippet1, snippet2, null)
                                .getNodeMatchScores()));
        for (Pair<Variable, Variable> variableMatch : variableMatches)
            System.out.println(variableMatch.getFirst().getName() + "<==>" + variableMatch.getSecond().getName());
    }

    private static void testActionGeneration(String nBef, String nAft) throws FileNotFoundException {
        Snippet snippetBef = readSnippet(nBef);
        Snippet snippetAft = readSnippet(nAft);

        Patch patch = new Patch(snippetBef, snippetAft);

        List<Action> actions = ActionGenerator.generate(snippetBef, snippetAft,
                General.getGreedyMatches(patch.befAftMatcher.getNodeMatchScores()));
    }

    public static void main(String[] args) throws Exception {
//        testVariableMatching("6", "5");
//        testNodeMatching("10", "9");
//        testSameProramNodeMatching("10", "10p");

//        why when i add the doodool function, it removes the append function?
//        why in 10 vs 10p node matching the two ifs do not match?
//        even if they (the two ifs) do not match, why it gives error when creating patch for 9?

        testPatchGenerationToFile("10", "10p", "9");
//        testPatchGeneration("10", "10p", "9");
//        testSameProgramVariableMatching("2", "2p");
//        testActionGeneration("2", "2p");

//        testIterativeNodeMatching(readSnippet("17"), readSnippet("18"));
    }
}
