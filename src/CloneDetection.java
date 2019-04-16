import New.AST.Node;
import New.AST.SnippetConverter;
import New.AST.SnippetConverter.Snippet;
import New.NodeMatcher;
import Utils.BiMap;
import Utils.General;
import Utils.Pair;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class CloneDetection {

    private static final double THRESHOLD = .93;

    static class MethodStruct {
        public File file;
        public int startPos;
        public int endPos;
        public Snippet snippet;

        public MethodStruct(File file, int startPos, int endPos, Snippet snippet) {
            this.file = file;
            this.startPos = startPos;
            this.endPos = endPos;
            this.snippet = snippet;
        }
    }

    private static List<MethodStruct> getMethods(File file) throws FileNotFoundException {
        System.out.println("started getting methods of " + file.getName());

        List<MethodStruct> result = new ArrayList<>();

        Scanner scan = new Scanner(file);
        scan.useDelimiter("\\Z");
        String content = scan.next();
        scan.close();

        CtClass<?> clazz;
        try {
            clazz = Launcher.parseClass(content);
        } catch (RuntimeException ex) {
            System.out.println("error in file " + file.getName() + " because of \"" + ex.getMessage() + "\"");
            return result;
        }
        for (CtMethod<?> method : clazz.getMethods()) {
            try {
                Snippet methodSnippet = new SnippetConverter().convertToSnippet(method.getBody());
//                if (methodBlock.count() >= 30)
                result.add(new MethodStruct(file, method.getPosition().getLine(),
                        method.getPosition().getEndLine(), methodSnippet));
            } catch (RuntimeException exception) {
                System.out.println("error in method " + method.getSimpleName() + " because of \"" + exception.getMessage() + "\"");
            }
        }

        return result;
    }

    private static void addToMethods(File folder, List<MethodStruct> methods) throws FileNotFoundException {
        for (File file : folder.listFiles())
            if (file.isDirectory())
                addToMethods(file, methods);
            else if (file.getName().endsWith(".java")) {
                methods.addAll(getMethods(file));
                System.out.println("number of successfully detected methods: " + methods.size());
            }
    }

    private static void findClones(String folderName, String outputFileName) throws IOException {
        PrintWriter writer = new PrintWriter(outputFileName, "UTF-8");

        List<MethodStruct> methods = new ArrayList<>();
        addToMethods(new File(folderName), methods);

        for (int i = 0; i < methods.size(); i++) {
            System.out.println("started comparing " + methods.get(i).file.getName());
            for (int j = i + 1; j < methods.size(); j++) {
                BiMap<Node, Node, Double> nodeNodeMap;
//                try {
                NodeMatcher matcher = new NodeMatcher(methods.get(i).snippet,
                        methods.get(j).snippet, null);
                nodeNodeMap = matcher.getNodeMatchScores();
//                } catch (RuntimeException ex) {
//                    System.out.println("An Exception!");
//                    continue;
//                }
                double score = nodeNodeMap.get(methods.get(i).snippet.getRoot(), methods.get(j).snippet.getRoot());
                if (score >= THRESHOLD) {
                    writer.println(methods.get(i).file.getParentFile().getName() + "," + methods.get(i).file.getName() + "," +
                            methods.get(i).startPos + "," + methods.get(i).endPos + "," +
                            methods.get(j).file.getParentFile().getName() + "," + methods.get(j).file.getName() + "," +
                            methods.get(j).startPos + "," + methods.get(j).endPos + "," + score);

                    List<Pair<Node, Node>> nodeMatchList = General.getGreedyMatches(nodeNodeMap);

                    for (var match : General.getGreedyMatches(NodeMatcher.computeVariableMatchScores(nodeNodeMap)))
                        System.out.println(match.getFirst().getName() + " ==>  " + match.getSecond().getName());
                    System.out.println(methods.get(i).file.getName() + "," +
                            methods.get(i).startPos + "," + methods.get(i).endPos);
                    System.out.println(methods.get(j).file.getName() + "," +
                            methods.get(j).startPos + "," + methods.get(j).endPos);

                    Main.printStyledSnippets(methods.get(i).snippet, methods.get(j).snippet, nodeMatchList);

                    System.out.println(score);
                    System.in.read();
                }
            }
        }

        writer.close();
    }

    public static void main(String[] args) throws IOException {
        findClones("F:/Education/Research Projects/PatchMining/BigCloneEval/ijadataset/bcb_reduced/2/sample",
                "2.sample.csv");
    }
}
