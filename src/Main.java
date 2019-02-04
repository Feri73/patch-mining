import AST.Node;
import AST.NodeConverter;
import Seq.SequenceDistanceCalculator;
import Utils.StyledPrinter;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

// one other idea is to do this local+global context matching with gumtree algorithm (in which the criteria of matching two subtrees is not only the subtrees themeselves, but the path from root of the programs to root of the trees + the matched variables)

//  write unit tests for everything:
//  most (if not all) functionalities should be symmetric. 1. if inputs are swapped. if two vars are completely treated the same in the program
//  test for applying funcitonalities on two completely identical programs

// do not foget to match the inputs in addition to ariables
public class Main {

    private static String nonVerbosePathToString(List<Node.PathElement> path) {
        StringBuilder res = new StringBuilder();
        for (Node.PathElement elem : path)
            res.append(elem.node.label + ":" + elem.node.value + ":" + elem.childEdgeLabel).append("--> ");
        return res + "\n";
    }

    private static boolean isValidValueSource(Node node) {
        return node.label == Node.NodeLabel.Value && node.sources.size() == 1 && (node.sources.contains(Node.ValueSource.Variable) || node.sources.contains(Node.ValueSource.Input));
    }

    // for every path p1 in one program containing variable v1, for every variable v2 in the other program, find the path p2 containing v2 that yeilds the best p1-p2 alignment score (conditioned on v1 and v2 be aligned together), and store this score for this p1,v2 pair
    private static Map.Entry<HashMap<List<Node.PathElement>, HashMap<String, Double>>, HashMap<List<Node.PathElement>, HashMap<String, Double>>> getPathVarMaps(List<List<Node.PathElement>> firstPaths, List<List<Node.PathElement>> secondPaths) {
        HashMap<List<Node.PathElement>, HashMap<String, Double>> firstPathVarMap = new HashMap<>();
        HashMap<List<Node.PathElement>, HashMap<String, Double>> secondPathVarMap = new HashMap<>();
        for (List<Node.PathElement> firstPath : firstPaths) {
            for (List<Node.PathElement> secondPath : secondPaths) {
                Node.PathElement firstLast = firstPath.get(firstPath.size() - 1);
                Node.PathElement secondLast = secondPath.get(secondPath.size() - 1);
                if (isValidValueSource(firstLast.node) && isValidValueSource(secondLast.node)) {
                    if (!firstPathVarMap.containsKey(firstPath))
                        firstPathVarMap.put(firstPath, new HashMap<>());
                    if (!secondPathVarMap.containsKey(secondPath))
                        secondPathVarMap.put(secondPath, new HashMap<>());
                    // to force that the two variables match, I exclude the last
                    SequenceDistanceCalculator.Matching<Node.PathElement> matching = SequenceDistanceCalculator.calculate(
                            firstPath.subList(0, firstPath.size() - 1), secondPath.subList(0, secondPath.size() - 1),
                            Node::getPenalty);
                    // is 1/l the best option i have (e.g. think about why we do not average the sequence elements alignment scores and instead add them? does it mean that we do not need length normalization at all here?). also shouldn't i add 1/l in sequence alignment algorithm instead of here?
                    double score = matching.score + 1.0 / Double.max(firstPath.size(), secondPath.size())
                            + Node.getPenalty(firstLast, secondLast);
                    HashMap<String, Double> firstVarMap = firstPathVarMap.get(firstPath);
                    HashMap<String, Double> secondVarMap = secondPathVarMap.get(secondPath);
                    if (!firstVarMap.containsKey(secondLast.node.value))
                        firstVarMap.put(secondLast.node.value, Double.MAX_VALUE);
                    if (!secondVarMap.containsKey(firstLast.node.value))
                        secondVarMap.put(firstLast.node.value, Double.MAX_VALUE);
                    if (score < firstVarMap.get(secondLast.node.value))
                        firstVarMap.put(secondLast.node.value, score);
                    if (score < secondVarMap.get(firstLast.node.value))
                        secondVarMap.put(firstLast.node.value, score);
                }
            }
        }
        return new AbstractMap.SimpleEntry<>(firstPathVarMap, secondPathVarMap);
    }

    // among all paths p1 in one program containing variable v1, averages the scores <p1,v2> for every v2 (from the previous function) and stores it in v1,v2 pairs
    private static HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> getVarVarMap(HashMap<List<Node.PathElement>, HashMap<String, Double>> targetPathVarMap, Set<String> helpreVariables) {
        HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> targetVarVarMap = new HashMap<>();
        for (List<Node.PathElement> path : targetPathVarMap.keySet()) {
            Node last = path.get(path.size() - 1).node;
            HashMap<String, Double> map = targetPathVarMap.get(path);
            if (!targetVarVarMap.containsKey(last.value))
                targetVarVarMap.put(last.value, new HashMap<>());
            HashMap<String, Map.Entry<Double, Integer>> targetMap = targetVarVarMap.get(last.value);
            for (String var : helpreVariables) {
                if (!targetMap.containsKey(var))
                    targetMap.put(var, new AbstractMap.SimpleEntry<>(0.0, 0));
                double mean = targetMap.get(var).getKey();
                int num = targetMap.get(var).getValue();
                // any better way other than mean?
                targetMap.put(var, new AbstractMap.SimpleEntry<>((mean * num + map.get(var)) / ((double) num + 1), num + 1));
            }
            targetVarVarMap.put(last.value, targetMap);
        }
        return targetVarVarMap;
    }

    private static HashMap<String, HashMap<String, Double>> getMeanVarVarMap(HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> firstVarVarMap, HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> secondVarVarMap) {
        HashMap<String, HashMap<String, Double>> mean = new HashMap<>();
        for (String var1 : firstVarVarMap.keySet()) {
            mean.put(var1, new HashMap<>());
            HashMap<String, Double> map = mean.get(var1);
            for (String var2 : secondVarVarMap.keySet()) {
                if (firstVarVarMap.get(var1).containsKey(var2) && secondVarVarMap.get(var2).containsKey(var1))
                    // any better way other then mean?
                    map.put(var2, (firstVarVarMap.get(var1).get(var2).getKey() * firstVarVarMap.get(var1).get(var2).getValue() + secondVarVarMap.get(var2).get(var1).getKey() * secondVarVarMap.get(var2).get(var1).getValue()) / (firstVarVarMap.get(var1).get(var2).getValue() + secondVarVarMap.get(var2).get(var1).getValue()));
            }
        }
        return mean;
    }

    private static <T> HashMap<T, T> getMatches(HashMap<T, HashMap<T, Double>> varVarMap) {
        // this should be ungreedy (n-queens with values in each cell and a loss function to be minimized) (or shouldn't?)
        HashMap<T, T> varMatching = new HashMap<>();
        while (true) {
            double currentMin = Double.MAX_VALUE;
            Map.Entry<T, T> currentMatching = null;
            for (T v1 : varVarMap.keySet())
                for (T v2 : varVarMap.get(v1).keySet())
                    if (!varMatching.keySet().contains(v1) && !varMatching.values().contains(v2) && varVarMap.get(v1).get(v2) < currentMin) {
                        currentMin = varVarMap.get(v1).get(v2);
                        currentMatching = new AbstractMap.SimpleEntry<>(v1, v2);
                    }
            if (currentMatching == null)
                break;
            varMatching.put(currentMatching.getKey(), currentMatching.getValue());
        }
        return varMatching;
    }

    private static Map.Entry<HashMap<List<Node.PathElement>, Map.Entry<List<Node.PathElement>, SequenceDistanceCalculator.Matching<Node.PathElement>>>,
            HashMap<List<Node.PathElement>, Map.Entry<List<Node.PathElement>, SequenceDistanceCalculator.Matching<Node.PathElement>>>>
    getPathPathMaps(List<List<Node.PathElement>> firstPaths, List<List<Node.PathElement>> secondPaths) {
        HashMap<List<Node.PathElement>, Map.Entry<List<Node.PathElement>, SequenceDistanceCalculator.Matching<Node.PathElement>>> firstPathPathMap = new HashMap<>();
        HashMap<List<Node.PathElement>, Map.Entry<List<Node.PathElement>, SequenceDistanceCalculator.Matching<Node.PathElement>>> secondPathPathMap = new HashMap<>();

        for (List<Node.PathElement> path1 : firstPaths)
            for (List<Node.PathElement> path2 : secondPaths) {
                var firstEntry = firstPathPathMap.getOrDefault(path1, new AbstractMap.SimpleEntry<>(null, new SequenceDistanceCalculator.Matching<Node.PathElement>(null, Double.POSITIVE_INFINITY, null, null)));
                var secondEntry = secondPathPathMap.getOrDefault(path2, new AbstractMap.SimpleEntry<>(null, new SequenceDistanceCalculator.Matching<Node.PathElement>(null, Double.POSITIVE_INFINITY, null, null)));
                SequenceDistanceCalculator.Matching<Node.PathElement> matching = SequenceDistanceCalculator.calculate(path1, path2, Node::getPenalty);
                matching.score += 1.0 / Double.max(path1.size(), path2.size());
                firstPathPathMap.put(path1.stream().map(x -> new Node.PathElement(x.node, x.childEdgeLabel, x.valueSources)).collect(Collectors.toList()), new AbstractMap.SimpleEntry<>(path2, matching));
                secondPathPathMap.put(path2.stream().map(x -> new Node.PathElement(x.node, x.childEdgeLabel, x.valueSources)).collect(Collectors.toList()), new AbstractMap.SimpleEntry<>(path1, matching));
                // uncomment these and comment two above statements to go for the mode that each path hsa one best path
//                if (matching.score < firstEntry.getValue().score)
//                    firstPathPathMap.put(path1, new AbstractMap.SimpleEntry<>(path2, matching));
//                if (matching.score < secondEntry.getValue().score)
//                    secondPathPathMap.put(path2, new AbstractMap.SimpleEntry<>(path1, matching));
            }

        return new AbstractMap.SimpleEntry<>(firstPathPathMap, secondPathPathMap);
    }

    // IMPORTANT:::::::::::::::::::::::::====> why some node pairs do not have score?(are not printed at all)
    private static void updateNodeNodeMap(SequenceDistanceCalculator.Matching<Node.PathElement> matching, HashMap<Node, HashMap<Node, Map.Entry<Double, Double>>> nodeNodeMap) {
        //which one?
//                double score = Math.exp(-(matching.score + 1.0 / Double.max(path1.size(), path2.size())));
        double score = 1.0 / matching.score;
        for (Map.Entry<Node.PathElement, Node.PathElement> match : matching.matching) {
            if (match.getKey() == null)
                continue;
            Node node1 = match.getKey().node;

            for (Map.Entry<Node.PathElement, Node.PathElement> matchP : matching.matching) {
                if (match == matchP)
                    continue;

                if (matchP.getValue() != null) {
                    Node node2P = matchP.getValue().node;
                    HashMap<Node, Map.Entry<Double, Double>> innerMapP = nodeNodeMap.getOrDefault(node1, new HashMap<>());
                    Map.Entry<Double, Double> currentEntryP = innerMapP.getOrDefault(node2P, new AbstractMap.SimpleEntry<>(0.0, 0.0));
                    innerMapP.put(node2P, new AbstractMap.SimpleEntry<>(currentEntryP.getValue() * currentEntryP.getKey() / (currentEntryP.getValue() + (1 + score)), currentEntryP.getValue() + (1 + score)));
                    nodeNodeMap.put(node1, innerMapP);
                }
            }

            if (match.getValue() == null)
                continue;

            Node node2 = match.getValue().node;

            HashMap<Node, Map.Entry<Double, Double>> innerMap = nodeNodeMap.getOrDefault(node1, new HashMap<>());
            Map.Entry<Double, Double> currentEntry = innerMap.getOrDefault(node2, new AbstractMap.SimpleEntry<>(0.0, 0.0));
            innerMap.put(node2, new AbstractMap.SimpleEntry<>((currentEntry.getValue() * currentEntry.getKey() + score * score) / (currentEntry.getValue() + (1 + score)), currentEntry.getValue() + (1 + score)));
            nodeNodeMap.put(node1, innerMap);
        }
    }

    // one other way to do this is to do it like getPathVarMaps method: for every two paths p1 and p2, for every node n1 in  p1 and every node n2 in p2, force that their are matched and then compute the path score in this condition
    // another modification is to instead of adding the varmatch to Node, add varmatchscores (for all pairs) to Node
    // computes the score of two nodes matching together as this: for paths that match with score s and in that n1 and n2 match too, add s to nominator and 1 to denominator. for paths that contain n1 and n2 but in their matching they do not match, only add denominator by 1 (this is for normalization). nominator/denominator is the score of n1 and n2 matching in the programs
    private static HashMap<Node, HashMap<Node, Map.Entry<Double, Double>>> getNodeNodeMap(
            HashMap<List<Node.PathElement>, Map.Entry<List<Node.PathElement>,
                    SequenceDistanceCalculator.Matching<Node.PathElement>>> firstPathPathMap,
            HashMap<List<Node.PathElement>, Map.Entry<List<Node.PathElement>,
                    SequenceDistanceCalculator.Matching<Node.PathElement>>> secondPathPathMap) {
        HashMap<Node, HashMap<Node, Map.Entry<Double, Double>>> result = new HashMap<>();

        for (List<Node.PathElement> path1 : firstPathPathMap.keySet()) {
            // this has length-normalized score. no need to normalize it here
            SequenceDistanceCalculator.Matching<Node.PathElement> matching = firstPathPathMap.get(path1).getValue();
            updateNodeNodeMap(matching, result);
        }

        for (List<Node.PathElement> path2 : secondPathPathMap.keySet()) {
            // this has length-normalized score. no need to normalize it here
            SequenceDistanceCalculator.Matching<Node.PathElement> matching = secondPathPathMap.get(path2).getValue();
            updateNodeNodeMap(matching, result);
        }

        return result;
    }

    private static void testGetPathVarMaps(Node p1, Node p2) {
        List<List<Node.PathElement>> firstPaths = p1.getAllPaths();
        List<List<Node.PathElement>> secondPaths = p2.getAllPaths();

        var pathVarMaps = getPathVarMaps(firstPaths, secondPaths);
        HashMap<List<Node.PathElement>, HashMap<String, Double>> firstPathVarMap = pathVarMaps.getKey();
        HashMap<List<Node.PathElement>, HashMap<String, Double>> secondPathVarMap = pathVarMaps.getValue();

        for (var path : firstPathVarMap.keySet()) {
            System.out.println(nonVerbosePathToString(path));
            for (var var : firstPathVarMap.get(path).keySet())
                System.out.println("\t\t" + var + " ==>  " + firstPathVarMap.get(path).get(var));
        }

        System.out.println("--------");

        for (var path : secondPathVarMap.keySet()) {
            System.out.println(nonVerbosePathToString(path));
            for (var var : secondPathVarMap.get(path).keySet())
                System.out.println("\t\t" + var + " ==>  " + secondPathVarMap.get(path).get(var));
        }
    }

    // test symmetry extensively and automatically
    // average the two (x,y)and (y,x) or not? (if i average, it is not good(test same program to see it))
    private static void testGetMeanVarVarMap(Node p1, Node p2) {
        List<List<Node.PathElement>> firstPaths = p1.getAllPaths();
        List<List<Node.PathElement>> secondPaths = p2.getAllPaths();

        var pathVarMaps = getPathVarMaps(firstPaths, secondPaths);
        HashMap<List<Node.PathElement>, HashMap<String, Double>> firstPathVarMap = pathVarMaps.getKey();
        HashMap<List<Node.PathElement>, HashMap<String, Double>> secondPathVarMap = pathVarMaps.getValue();

        Set<String> firstVars = secondPathVarMap.values().stream().findAny().get().keySet();
        Set<String> secondVars = firstPathVarMap.values().stream().findAny().get().keySet();

        HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> firstVarVarMap = getVarVarMap(firstPathVarMap, secondVars);
        HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> secondVarVarMap = getVarVarMap(secondPathVarMap, firstVars);

        HashMap<String, HashMap<String, Double>> meanVarVarMap = getMeanVarVarMap(firstVarVarMap, secondVarVarMap);

        for (var var1 : meanVarVarMap.keySet()) {
            System.out.println(var1);
            for (var var2 : meanVarVarMap.get(var1).keySet())
                System.out.println("\t\t" + var2 + " ==>  " + meanVarVarMap.get(var1).get(var2));
        }
    }

    private static void testGetVarMatches(Node p1, Node p2) {
        List<List<Node.PathElement>> firstPaths = p1.getAllPaths();
        List<List<Node.PathElement>> secondPaths = p2.getAllPaths();

        var pathVarMaps = getPathVarMaps(firstPaths, secondPaths);
        HashMap<List<Node.PathElement>, HashMap<String, Double>> firstPathVarMap = pathVarMaps.getKey();
        HashMap<List<Node.PathElement>, HashMap<String, Double>> secondPathVarMap = pathVarMaps.getValue();

        Set<String> firstVars = secondPathVarMap.values().stream().findAny().get().keySet();
        Set<String> secondVars = firstPathVarMap.values().stream().findAny().get().keySet();

        HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> firstVarVarMap = getVarVarMap(firstPathVarMap, secondVars);
        HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> secondVarVarMap = getVarVarMap(secondPathVarMap, firstVars);

        HashMap<String, HashMap<String, Double>> meanVarVarMap = getMeanVarVarMap(firstVarVarMap, secondVarVarMap);

        HashMap<String, String> varMatches = getMatches(meanVarVarMap);

        for (var var1 : varMatches.keySet()) {
            System.out.println(var1 + " ==>  " + varMatches.get(var1));
        }
    }

    private static void testNodeConverter(Node p1, Node p1p) {
        System.out.println(p1.subtreeEquals(p1p));
    }

    private static double testGetNodeNodeMap(Node p1, Node p2) {
        List<List<Node.PathElement>> firstPaths = p1.getAllPaths();
        List<List<Node.PathElement>> secondPaths = p2.getAllPaths();

        var pathVarMaps = getPathVarMaps(firstPaths, secondPaths);
        HashMap<List<Node.PathElement>, HashMap<String, Double>> firstPathVarMap = pathVarMaps.getKey();
        HashMap<List<Node.PathElement>, HashMap<String, Double>> secondPathVarMap = pathVarMaps.getValue();

        Set<String> firstVars = secondPathVarMap.values().stream().findAny().get().keySet();
        Set<String> secondVars = firstPathVarMap.values().stream().findAny().get().keySet();

        HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> firstVarVarMap = getVarVarMap(firstPathVarMap, secondVars);
        HashMap<String, HashMap<String, Map.Entry<Double, Integer>>> secondVarVarMap = getVarVarMap(secondPathVarMap, firstVars);

        HashMap<String, HashMap<String, Double>> meanVarVarMap = getMeanVarVarMap(firstVarVarMap, secondVarVarMap);

        HashMap<String, String> varMatches = getMatches(meanVarVarMap);

        Node.variableMatchings = varMatches;

        var pathPathMaps = getPathPathMaps(firstPaths, secondPaths);
        HashMap<Node, HashMap<Node, Map.Entry<Double, Double>>> verboseNodeNodeMap = getNodeNodeMap(pathPathMaps.getKey(), pathPathMaps.getValue());
        HashMap<Node, HashMap<Node, Double>> nodeNodeMap = new HashMap<>();
        for (Node node1 : verboseNodeNodeMap.keySet()) {
            nodeNodeMap.put(node1, new HashMap<>());
            for (Node node2 : verboseNodeNodeMap.get(node1).keySet()) {
                // if in the "which one" comment i choose second, i should here also choose second and vice versa
//                nodeNodeMap.get(node1).put(node2, -Math.log(verboseNodeNodeMap.get(node1).get(node2).getKey()));
                nodeNodeMap.get(node1).put(node2, 1 / verboseNodeNodeMap.get(node1).get(node2).getKey());
            }
        }

//        for (Node node1 : nodeNodeMap.keySet()) {
//            System.out.println(node1.parent + "-->" + node1);
//            for (Node node2 : nodeNodeMap.get(node1).keySet())
//                System.out.println("\t\t" + node2.parent + "-->" + node2 + " ==>  " + nodeNodeMap.get(node1).get(node2));
//        }

        return nodeNodeMap.get(p1).get(p2);

//         this is not the best option i have. in fact this is not good at all for nodes. CHANGE IT
//        HashMap<Node, Node> nodeMatches = getMatches(nodeNodeMap);

//        int styleIndex = 0;
//        Field[] styleFields = StyledPrinter.class.getFields();
//        for (Node node1 : nodeMatches.keySet())
//            try {
//                styleIndex %= styleFields.length;
//                while ("RESET".equals(styleFields[styleIndex].getName()) || styleFields[styleIndex].getName().contains("WHITE") || styleFields[styleIndex].getName().contains("BLACK"))
//                    styleIndex = (styleIndex + 1) % styleFields.length;
//                node1.styleCode = (String) styleFields[styleIndex].get(null);
//                nodeMatches.get(node1).styleCode = node1.styleCode;
//                styleIndex++;
//            } catch (IllegalAccessException e) {
//                throw new RuntimeException();
//            }

//        System.out.println();
//        System.out.println(p1.toStyledString(""));
//        System.out.println();
//        System.out.println(p2.toStyledString(""));

//        System.out.println(nodeNodeMap.get(p1).get(p2));
    }

    private static void testPathMatching(List<Node.PathElement> p1, List<Node.PathElement> p2) {
        SequenceDistanceCalculator.Matching<Node.PathElement> matching = SequenceDistanceCalculator.calculate(p1, p2, Node::getPenalty);
        for (var match : matching.matching)
            System.out.println(match.getKey() + "-->" + match.getValue());
    }

    static class MethodStruct {
        public File file;
        public int startPos;
        public int endPos;
        public Node root;

        public MethodStruct(File file, int startPos, int endPos, Node root) {
            this.file = file;
            this.startPos = startPos;
            this.endPos = endPos;
            this.root = root;
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
                result.add(new MethodStruct(file, method.getPosition().getLine(), method.getPosition().getEndLine(), new NodeConverter().element(method.getBody()).get(0)));
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

    private static void findClones(String folderName, String outputFileName) throws FileNotFoundException, UnsupportedEncodingException {
        File codeFolder = new File(folderName);
        PrintWriter writer = new PrintWriter(outputFileName, "UTF-8");

        List<MethodStruct> methods = new ArrayList<>();
        addToMethods(new File(folderName), methods);

        for (int i = 0; i < methods.size(); i++) {
            System.out.println("started comparing " + methods.get(i).file.getName());
            for (int j = i + 1; j < methods.size(); j++) {
                double score;
                try {
                    score = testGetNodeNodeMap(methods.get(i).root, methods.get(j).root);
                } catch (RuntimeException ex) {
                    continue;
                }
                if (score <= 20)
                    writer.println(methods.get(i).file.getParentFile().getName() + "," + methods.get(i).file.getName() + "," +
                            methods.get(i).startPos + "," + methods.get(i).endPos + "," +
                            methods.get(j).file.getParentFile().getName() + "," + methods.get(j).file.getName() + "," +
                            methods.get(j).startPos + "," + methods.get(j).endPos);
            }
        }

        writer.close();
    }

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        findClones("F:/Education/Research Projects/PatchMining/BigCloneEval/ijadataset/bcb_reduced/2/default",
                "2.default.csv");
//        Node p1 = readFile("7.java");
//        Node p2 = readFile("8.java");
//        Node p1 = firstProgram();
//        Node p2 = secondProgram();

//        testPathMatching(p1.getAllPaths().get(2), p2.getAllPaths().get(2));

        // IMPORTANT: SOLVE ISSUE FOR 9 AND 10 PROGRAMS
//        testGetNodeNodeMap(p1, p2);
//        testGetVarMatches(p1, p2);
//        testGetMeanVarVarMap(p1, p2);
//        testNodeConverter(readFile("3.java"), thirdProgram());

    }

    private static Node readFile(String name) throws FileNotFoundException {
        Scanner scan = new Scanner(new File(name));
        scan.useDelimiter("\\Z");
        String content = scan.next();
        scan.close();

        CtClass<?> clazz = Launcher.parseClass(content);
        return new NodeConverter().element(clazz.getMethodsByName("main").get(0).getBody()).get(0);
    }

    public static void testPaths_alignment_similarity(Node p) {
        List<List<Node.PathElement>> paths = p.getAllPaths();
        for (List<Node.PathElement> path : paths)
            System.out.println(path.size() + ": " + path);

//        System.out.println(Node.sourcesSimilarity(EnumSet.of(Node.ValueSource.Variable), EnumSet.of(Node.ValueSource.Literal)));
//        System.out.println(Node.sourcesSimilarity(EnumSet.of(Node.ValueSource.Variable, Node.ValueSource.Literal), EnumSet.of(Node.ValueSource.Literal)));
//        System.out.println(Node.sourcesSimilarity(EnumSet.noneOf(Node.ValueSource.class), EnumSet.of(Node.ValueSource.Literal)));
//        System.out.println(Node.sourcesSimilarity(EnumSet.of(Node.ValueSource.Variable, Node.ValueSource.Literal), EnumSet.of(Node.ValueSource.Literal, Node.ValueSource.Input)));
//        System.out.println(Node.sourcesSimilarity(EnumSet.of(Node.ValueSource.Variable, Node.ValueSource.Literal), EnumSet.of(Node.ValueSource.Literal, Node.ValueSource.Variable)));
    }

    public static Node firstProgram() {
        Node OuterBlock = new Node(Node.NodeLabel.Block, null);
        Node InnerBlock = new Node(Node.NodeLabel.Block, null);
        Node If = new Node(Node.NodeLabel.Branch, "if");
        Node Equals = new Node(Node.NodeLabel.CompareOperator, "==", EnumSet.of(Node.ValueSource.Variable), Node.Type.Boolean);
        Node I = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Node.ValueSource.Variable), Node.Type.Integer);
        Node J = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Node.ValueSource.Variable), Node.Type.Integer);
        Node Assign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Node.ValueSource.Input, Node.ValueSource.Literal), Node.Type.Integer);
        Node A = new Node(Node.NodeLabel.Value, "a", EnumSet.of(Node.ValueSource.Input), Node.Type.Integer);
        Node Two = new Node(Node.NodeLabel.Value, "2", EnumSet.of(Node.ValueSource.Literal), Node.Type.Integer);
        Node AddIAssigner = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Node.ValueSource.Variable), Node.Type.Integer);
        Node AddIAssignee = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Node.ValueSource.Variable), Node.Type.Integer);
        Node AddITwo = new Node(Node.NodeLabel.Value, "2", EnumSet.of(Node.ValueSource.Literal), Node.Type.Integer);
        Node AddIAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Node.ValueSource.Variable, Node.ValueSource.Literal), Node.Type.Integer);
        Node AddIPlus = new Node(Node.NodeLabel.ArithmeticOperator, "+", EnumSet.of(Node.ValueSource.Variable, Node.ValueSource.Literal), Node.Type.Integer);

        AddIPlus.addChild(AddIAssigner, Node.EdgeLabel.Operand);
        AddIPlus.addChild(AddITwo, Node.EdgeLabel.Operand);
        AddIAssign.addChild(AddIAssignee, Node.EdgeLabel.Assignee);
        AddIAssign.addChild(AddIPlus, Node.EdgeLabel.Assigner);
        Assign.addChild(A, Node.EdgeLabel.Assignee);
        Assign.addChild(Two, Node.EdgeLabel.Assigner);
        Equals.addChild(I, Node.EdgeLabel.Operand);
        Equals.addChild(J, Node.EdgeLabel.Operand);
        If.addChild(Equals, Node.EdgeLabel.Condition);
        If.addChild(InnerBlock, Node.EdgeLabel.Block);
        InnerBlock.addChild(AddIAssign, Node.EdgeLabel.Statement);
        InnerBlock.addChild(Assign, Node.EdgeLabel.Statement);
        OuterBlock.addChild(If, Node.EdgeLabel.Statement);

        return OuterBlock;
    }

    public static Node secondProgram() {
        Node OuterBlock = new Node(Node.NodeLabel.Block, null);
        Node InnerBlock = new Node(Node.NodeLabel.Block, null);
        Node AssignX = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Node.ValueSource.Variable), Node.Type.Boolean);
        Node Equals = new Node(Node.NodeLabel.CompareOperator, "==", EnumSet.of(Node.ValueSource.Variable), Node.Type.Boolean);
        Node H = new Node(Node.NodeLabel.Value, "h", EnumSet.of(Node.ValueSource.Variable), Node.Type.Integer);
        Node K = new Node(Node.NodeLabel.Value, "k", EnumSet.of(Node.ValueSource.Variable), Node.Type.Integer);
        Node FirstX = new Node(Node.NodeLabel.Value, "x", EnumSet.of(Node.ValueSource.Variable), Node.Type.Boolean);
        Node If = new Node(Node.NodeLabel.Branch, "if");
        Node X = new Node(Node.NodeLabel.Value, "x", EnumSet.of(Node.ValueSource.Variable), Node.Type.Boolean);
        Node Assign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Node.ValueSource.Input, Node.ValueSource.Literal), Node.Type.Integer);
        Node B = new Node(Node.NodeLabel.Value, "b", EnumSet.of(Node.ValueSource.Input), Node.Type.Integer);
        Node Two = new Node(Node.NodeLabel.Value, "2", EnumSet.of(Node.ValueSource.Literal), Node.Type.Integer);
        Node AddKAssigner = new Node(Node.NodeLabel.Value, "k", EnumSet.of(Node.ValueSource.Variable), Node.Type.Integer);
        Node AddKAssignee = new Node(Node.NodeLabel.Value, "k", EnumSet.of(Node.ValueSource.Variable), Node.Type.Integer);
        Node AddKOne = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Node.ValueSource.Literal), Node.Type.Integer);
        Node AddKAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Node.ValueSource.Variable, Node.ValueSource.Literal), Node.Type.Integer);
        Node AddKPlus = new Node(Node.NodeLabel.ArithmeticOperator, "+", EnumSet.of(Node.ValueSource.Variable, Node.ValueSource.Literal), Node.Type.Integer);

        Equals.addChild(H, Node.EdgeLabel.Operand);
        Equals.addChild(K, Node.EdgeLabel.Operand);
        AssignX.addChild(X, Node.EdgeLabel.Assignee);
        AssignX.addChild(Equals, Node.EdgeLabel.Assigner);
        AddKPlus.addChild(AddKAssigner, Node.EdgeLabel.Operand);
        AddKPlus.addChild(AddKOne, Node.EdgeLabel.Operand);
        AddKAssign.addChild(AddKAssignee, Node.EdgeLabel.Assignee);
        AddKAssign.addChild(AddKPlus, Node.EdgeLabel.Assigner);
        Assign.addChild(B, Node.EdgeLabel.Assignee);
        Assign.addChild(Two, Node.EdgeLabel.Assigner);
        If.addChild(X, Node.EdgeLabel.Condition);
        If.addChild(InnerBlock, Node.EdgeLabel.Block);
        InnerBlock.addChild(AddKAssign, Node.EdgeLabel.Statement);
        InnerBlock.addChild(Assign, Node.EdgeLabel.Statement);
        OuterBlock.addChild(AssignX, Node.EdgeLabel.Statement);
        OuterBlock.addChild(If, Node.EdgeLabel.Statement);

        return OuterBlock;
    }

    public static Node thirdProgram() {
        // 35 paths
        // 13 max path length
        // 74 nodes

        Node.ValueSource Variable = Node.ValueSource.Variable;
        Node.ValueSource Input = Node.ValueSource.Input;
        Node.ValueSource Literal = Node.ValueSource.Literal;

        Node method = new Node(Node.NodeLabel.Block, null);

        Node iInitAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Literal), Node.Type.Void);
        Node iInitAssignI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);
        Node iInitAssign0 = new Node(Node.NodeLabel.Value, "0", EnumSet.of(Literal), Node.Type.Integer);

        Node firstLoop = new Node(Node.NodeLabel.Loop, "for");

        Node firstLoopCond = new Node(Node.NodeLabel.CompareOperator, "LT", EnumSet.of(Variable, Input), Node.Type.Boolean);
        Node firstLoopCondI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);
        Node firstLoopCondCount = new Node(Node.NodeLabel.Value, "count", EnumSet.of(Input), Node.Type.Integer);

        Node firstLoopBlock = new Node(Node.NodeLabel.Block, null);

        Node chInitAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Input), Node.Type.Void);
        Node chInitAssignCh = new Node(Node.NodeLabel.Value, "ch", EnumSet.of(Variable), Node.Type.OtherClass);
        Node chInitAssignIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input), Node.Type.OtherClass);
        Node chInitAssignChans = new Node(Node.NodeLabel.Value, "chans", EnumSet.of(Input), Node.Type.OtherClass);
        Node chInitAssignIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable), null);
        Node chInitAssignArgsI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);

        Node vInitAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable), Node.Type.Void);
        Node vInitAssignV = new Node(Node.NodeLabel.Value, "v", EnumSet.of(Variable), Node.Type.String);
        Node vInitAssignGetTag = new Node(Node.NodeLabel.ObjectGetterCall, "getTag", EnumSet.of(Variable), Node.Type.String);
        Node vInitAssignCh = new Node(Node.NodeLabel.Value, "ch", EnumSet.of(Variable), Node.Type.OtherClass);
        Node vInitAssignGetTagArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.noneOf(Node.ValueSource.class), null);

        Node jiAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable), Node.Type.Void);
        Node jiAssignJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node jiAssignI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);

        Node secondLoop = new Node(Node.NodeLabel.Loop, "while");

        Node secondLoopCond = new Node(Node.NodeLabel.BooleanOperator, "AND", EnumSet.of(Variable, Input, Literal), Node.Type.Boolean);
        Node secondLoopCondJComp = new Node(Node.NodeLabel.CompareOperator, "GT", EnumSet.of(Variable, Literal), Node.Type.Boolean);
        Node secondLoopCondJCompJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node secondLoopCondJComp0 = new Node(Node.NodeLabel.Value, "0", EnumSet.of(Literal), Node.Type.Integer);
        Node secondLoopCondLengComp = new Node(Node.NodeLabel.CompareOperator, "GT", EnumSet.of(Variable, Input, Literal), Node.Type.Boolean);
        Node secondLoopCondCompare = new Node(Node.NodeLabel.ObjectMethodCall, "compare", EnumSet.of(Variable, Input, Literal), Node.Type.Integer);
        Node secondLoopCondCollator = new Node(Node.NodeLabel.Value, "collator", EnumSet.of(Input), Node.Type.OtherClass);
        Node secondLoopCondCompareArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable, Input, Literal), null);
        Node secondLoopCondGetTag = new Node(Node.NodeLabel.ObjectGetterCall, "getTag", EnumSet.of(Variable, Input, Literal), Node.Type.String); // the type of this is infered because of before when it is equaled to a string.
        Node secondLoopCondGetTagArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.noneOf(Node.ValueSource.class), null);
        Node secondLoopCondIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input, Literal), Node.Type.OtherClass);
        Node secondLoopCondChans = new Node(Node.NodeLabel.Value, "chans", EnumSet.of(Input), Node.Type.OtherClass);
        Node secondLoopCondIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable, Literal), null);
        Node secondLoopCondIndexMinus = new Node(Node.NodeLabel.ArithmeticOperator, "MINUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node secondLoopCondIndexMinusJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node secondLoopCondIndexMinus1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);
        Node secondLoopCondV = new Node(Node.NodeLabel.Value, "v", EnumSet.of(Variable), Node.Type.String);
        Node secondLoopCondLengComp0 = new Node(Node.NodeLabel.Value, "0", EnumSet.of(Literal), Node.Type.Integer);

        Node secondLoopBlock = new Node(Node.NodeLabel.Block, null);

        Node chansSwapAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Input, Literal), Node.Type.Void);
        Node chansSwapAssigneeIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input), Node.Type.OtherClass);
        Node chansSwapAssigneeChans = new Node(Node.NodeLabel.Value, "chans", EnumSet.of(Input), Node.Type.OtherClass);
        Node chansSwapAssigneeIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable), null);
        Node chansSwapAssigneeJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node chansSwapAssignerIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input, Literal), Node.Type.OtherClass);
        Node chansSwapAssignerChans = new Node(Node.NodeLabel.Value, "chans", EnumSet.of(Input), Node.Type.OtherClass);  // it is array and there should be an algorithm that can detect it
        Node chansSwapAssignerIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable, Literal), null);
        Node chansSwapAssignerMinus = new Node(Node.NodeLabel.ArithmeticOperator, "MINUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node chansSwapAssignerJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node chansSwapAssigner1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);

        Node jDecreaseAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Literal), Node.Type.Void);
        Node jDecreaseAssigneeJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node jDecreaseAssignerMinus = new Node(Node.NodeLabel.ArithmeticOperator, "MINUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node jDecreaseAssignerJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node jDecreaseAssigner1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);

        Node chansReplaceAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Input), Node.Type.Void);
        Node chansReplaceAssigneeIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input), Node.Type.OtherClass);
        Node chansReplaceAssigneeChans = new Node(Node.NodeLabel.Value, "chans", EnumSet.of(Input), Node.Type.OtherClass);
        Node chansReplaceAssigneeIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable), null);
        Node chansReplaceAssigneeJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), null);
        Node chansReplaceAssignerCh = new Node(Node.NodeLabel.Value, "ch", EnumSet.of(Variable), Node.Type.OtherClass);

        Node iIncreaseAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Literal), Node.Type.Void);
        Node iIncreaseAssigneeI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);
        Node iIncreaseAssignerPlus = new Node(Node.NodeLabel.ArithmeticOperator, "PLUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node iIncreaseAssignerI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);
        Node iIncreaseAssigner1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);


        iIncreaseAssignerPlus.addChild(iIncreaseAssignerI, Node.EdgeLabel.Operand);
        iIncreaseAssignerPlus.addChild(iIncreaseAssigner1, Node.EdgeLabel.Operand);
        iIncreaseAssign.addChild(iIncreaseAssignerPlus, Node.EdgeLabel.Assigner);
        iIncreaseAssign.addChild(iIncreaseAssigneeI, Node.EdgeLabel.Assignee);

        chansReplaceAssigneeIndexArgs.addChild(chansReplaceAssigneeJ, Node.EdgeLabel.MethodArgument);
        chansReplaceAssigneeIndex.addChild(chansReplaceAssigneeChans, Node.EdgeLabel.MethodCaller);
        chansReplaceAssigneeIndex.addChild(chansReplaceAssigneeIndexArgs, Node.EdgeLabel.MethodArguments);
        chansReplaceAssign.addChild(chansReplaceAssignerCh, Node.EdgeLabel.Assigner);
        chansReplaceAssign.addChild(chansReplaceAssigneeIndex, Node.EdgeLabel.Assignee);

        chansSwapAssignerMinus.addChild(chansSwapAssignerJ, Node.EdgeLabel.Operand);
        chansSwapAssignerMinus.addChild(chansSwapAssigner1, Node.EdgeLabel.Operand);
        chansSwapAssignerIndexArgs.addChild(chansSwapAssignerMinus, Node.EdgeLabel.MethodArgument);
        chansSwapAssignerIndex.addChild(chansSwapAssignerChans, Node.EdgeLabel.MethodCaller);
        chansSwapAssignerIndex.addChild(chansSwapAssignerIndexArgs, Node.EdgeLabel.MethodArguments);
        chansSwapAssigneeIndexArgs.addChild(chansSwapAssigneeJ, Node.EdgeLabel.MethodArgument);
        chansSwapAssigneeIndex.addChild(chansSwapAssigneeChans, Node.EdgeLabel.MethodCaller);
        chansSwapAssigneeIndex.addChild(chansSwapAssigneeIndexArgs, Node.EdgeLabel.MethodArguments);
        chansSwapAssign.addChild(chansSwapAssignerIndex, Node.EdgeLabel.Assigner);
        chansSwapAssign.addChild(chansSwapAssigneeIndex, Node.EdgeLabel.Assignee);

        jDecreaseAssignerMinus.addChild(jDecreaseAssignerJ, Node.EdgeLabel.Operand);
        jDecreaseAssignerMinus.addChild(jDecreaseAssigner1, Node.EdgeLabel.Operand);
        jDecreaseAssign.addChild(jDecreaseAssignerMinus, Node.EdgeLabel.Assigner);
        jDecreaseAssign.addChild(jDecreaseAssigneeJ, Node.EdgeLabel.Assignee);

        secondLoopBlock.addChild(chansSwapAssign, Node.EdgeLabel.Statement);
        secondLoopBlock.addChild(jDecreaseAssign, Node.EdgeLabel.Statement);

        secondLoopCondIndexMinus.addChild(secondLoopCondIndexMinusJ, Node.EdgeLabel.Operand);
        secondLoopCondIndexMinus.addChild(secondLoopCondIndexMinus1, Node.EdgeLabel.Operand);
        secondLoopCondIndexArgs.addChild(secondLoopCondIndexMinus, Node.EdgeLabel.MethodArgument);
        secondLoopCondIndex.addChild(secondLoopCondChans, Node.EdgeLabel.MethodCaller);
        secondLoopCondIndex.addChild(secondLoopCondIndexArgs, Node.EdgeLabel.MethodArguments);
        secondLoopCondGetTag.addChild(secondLoopCondIndex, Node.EdgeLabel.MethodCaller);
        secondLoopCondGetTag.addChild(secondLoopCondGetTagArgs, Node.EdgeLabel.MethodArguments);
        secondLoopCondCompareArgs.addChild(secondLoopCondGetTag, Node.EdgeLabel.MethodArgument);
        secondLoopCondCompareArgs.addChild(secondLoopCondV, Node.EdgeLabel.MethodArgument);
        secondLoopCondCompare.addChild(secondLoopCondCollator, Node.EdgeLabel.MethodCaller);
        secondLoopCondCompare.addChild(secondLoopCondCompareArgs, Node.EdgeLabel.MethodArguments);
        secondLoopCondLengComp.addChild(secondLoopCondCompare, Node.EdgeLabel.Operand);
        secondLoopCondLengComp.addChild(secondLoopCondLengComp0, Node.EdgeLabel.Operand);
        secondLoopCondJComp.addChild(secondLoopCondJCompJ, Node.EdgeLabel.Operand);
        secondLoopCondJComp.addChild(secondLoopCondJComp0, Node.EdgeLabel.Operand);
        secondLoopCond.addChild(secondLoopCondJComp, Node.EdgeLabel.Operand);
        secondLoopCond.addChild(secondLoopCondLengComp, Node.EdgeLabel.Operand);

        secondLoop.addChild(secondLoopCond, Node.EdgeLabel.Condition);
        secondLoop.addChild(secondLoopBlock, Node.EdgeLabel.Block);

        jiAssign.addChild(jiAssignI, Node.EdgeLabel.Assigner);
        jiAssign.addChild(jiAssignJ, Node.EdgeLabel.Assignee);

        vInitAssignGetTag.addChild(vInitAssignCh, Node.EdgeLabel.MethodCaller);
        vInitAssignGetTag.addChild(vInitAssignGetTagArgs, Node.EdgeLabel.MethodArguments);
        vInitAssign.addChild(vInitAssignGetTag, Node.EdgeLabel.Assigner);
        vInitAssign.addChild(vInitAssignV, Node.EdgeLabel.Assignee);

        chInitAssignIndex.addChild(chInitAssignChans, Node.EdgeLabel.MethodCaller);
        chInitAssignIndexArgs.addChild(chInitAssignArgsI, Node.EdgeLabel.MethodArgument);
        chInitAssignIndex.addChild(chInitAssignIndexArgs, Node.EdgeLabel.MethodArguments);
        chInitAssign.addChild(chInitAssignIndex, Node.EdgeLabel.Assigner);
        chInitAssign.addChild(chInitAssignCh, Node.EdgeLabel.Assignee);

        firstLoopBlock.addChild(chInitAssign, Node.EdgeLabel.Statement);
        firstLoopBlock.addChild(vInitAssign, Node.EdgeLabel.Statement);
        firstLoopBlock.addChild(jiAssign, Node.EdgeLabel.Statement);
        firstLoopBlock.addChild(secondLoop, Node.EdgeLabel.Statement);
        firstLoopBlock.addChild(chansReplaceAssign, Node.EdgeLabel.Statement);
        firstLoopBlock.addChild(iIncreaseAssign, Node.EdgeLabel.Statement);

        firstLoopCond.addChild(firstLoopCondI, Node.EdgeLabel.Operand);
        firstLoopCond.addChild(firstLoopCondCount, Node.EdgeLabel.Operand);

        firstLoop.addChild(firstLoopCond, Node.EdgeLabel.Condition);
        firstLoop.addChild(firstLoopBlock, Node.EdgeLabel.Block);

        iInitAssign.addChild(iInitAssign0, Node.EdgeLabel.Assigner);
        iInitAssign.addChild(iInitAssignI, Node.EdgeLabel.Assignee);

        method.addChild(iInitAssign, Node.EdgeLabel.Statement);
        method.addChild(firstLoop, Node.EdgeLabel.Statement);

        return method;
    }

    public static Node fourthProgram() {
        // 34 paths
        // 13 max path length
        // 75 nodes

        Node.ValueSource Variable = Node.ValueSource.Variable;
        Node.ValueSource Input = Node.ValueSource.Input;
        Node.ValueSource Literal = Node.ValueSource.Literal;

        Node method = new Node(Node.NodeLabel.Block, null);

        Node iInitAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Input, Literal), Node.Type.Void);
        Node iInitAssigneeI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);
        Node iInitAssignerMinus = new Node(Node.NodeLabel.ArithmeticOperator, "MINUS", EnumSet.of(Input, Literal), Node.Type.Integer);
        Node iInitAssignerLength = new Node(Node.NodeLabel.ObjectGetterCall, "length", EnumSet.of(Input), Node.Type.Integer);
        Node iInitAssignerLengthArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.noneOf(Node.ValueSource.class), null);
        Node iInitAssignerFilenames = new Node(Node.NodeLabel.Value, "filenames", EnumSet.of(Input), Node.Type.OtherClass); // it is array and there should be an algorithm that can detect this fact. for now because i dont have this algorithm i put it sa otherclass
        Node iInitAssigner1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);

        Node firstLoop = new Node(Node.NodeLabel.Loop, "for");

        Node firstLoopCond = new Node(Node.NodeLabel.CompareOperator, "GT", EnumSet.of(Variable, Literal), Node.Type.Boolean);
        Node firstLoopCondI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);
        Node firstLoopCond0 = new Node(Node.NodeLabel.Value, "0", EnumSet.of(Literal), Node.Type.Integer);

        Node firstLoopBlock = new Node(Node.NodeLabel.Block, null);

        Node jInitAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Literal), Node.Type.Void);
        Node jInitAssigneeJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node jInitAssigner0 = new Node(Node.NodeLabel.Value, "0", EnumSet.of(Literal), Node.Type.Integer);

        Node secondLoop = new Node(Node.NodeLabel.Loop, "for");

        Node secondLoopCond = new Node(Node.NodeLabel.CompareOperator, "LT", EnumSet.of(Variable), Node.Type.Boolean);
        Node secondLoopCondJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node secondLoopCondI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);

        Node secondLoopBlock = new Node(Node.NodeLabel.Block, null);

        Node innerIf = new Node(Node.NodeLabel.Branch, "if");

        Node innerIfCond = new Node(Node.NodeLabel.CompareOperator, "GT", EnumSet.of(Variable, Input, Literal), Node.Type.Boolean);
        Node innerIfCondCompare = new Node(Node.NodeLabel.ObjectMethodCall, "compareTo", EnumSet.of(Variable, Input, Literal), Node.Type.Integer);
        Node innerIfCondCompareArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable, Input, Literal), null);
        Node innerIfCondCompareArgsIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input, Literal), Node.Type.String); // how do i make automatically infer here it is string?
        Node innerIfCondCompareArgsFilenames = new Node(Node.NodeLabel.Value, "filenames", EnumSet.of(Input), Node.Type.OtherClass);
        Node innerIfCondCompareArgsIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable, Literal), null);
        Node innerIfCondCompareArgsPlus = new Node(Node.NodeLabel.ArithmeticOperator, "PLUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node innerIfCondCompareArgsJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node innerIfCondCompareArgs1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);
        Node innerIfCondCompareIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input), Node.Type.String);
        Node innerIfCondCompareFilenames = new Node(Node.NodeLabel.Value, "filenames", EnumSet.of(Input), Node.Type.OtherClass);
        Node innerIfCondCompareIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable), Node.Type.Integer);
        Node innerIfCondCompareJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node innerIfCond0 = new Node(Node.NodeLabel.Value, "0", EnumSet.of(Literal), Node.Type.Integer);

        Node innerIfBlock = new Node(Node.NodeLabel.Block, null);

        Node tempInitAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Input), Node.Type.Void);
        Node tempInitAssigneeTemp = new Node(Node.NodeLabel.Value, "temp", EnumSet.of(Variable), Node.Type.String);
        Node tempInitAssignerIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input), Node.Type.String);
        Node tempInitAssignerFilenames = new Node(Node.NodeLabel.Value, "filenames", EnumSet.of(Input), Node.Type.OtherClass);
        Node tempInitAssignerIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable), null);
        Node tempInitAssignerJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);

        Node filenamesSwapAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Input, Literal), Node.Type.Void);
        Node filenamesSwapAssigneeIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input), Node.Type.String);
        Node filenamesSwapAssigneeFilenames = new Node(Node.NodeLabel.Value, "filenames", EnumSet.of(Input), Node.Type.OtherClass);
        Node filenamesSwapAssigneeIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable), null);
        Node filenamesSwapAssigneeJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node filenamesSwapAssignerIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input, Literal), Node.Type.String);
        Node filenamesSwapAssignerFilenames = new Node(Node.NodeLabel.Value, "filenames", EnumSet.of(Input), Node.Type.OtherClass);
        Node filenamesSwapAssignerIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable, Literal), null);
        Node filenamesSwapAssignerPlus = new Node(Node.NodeLabel.ArithmeticOperator, "PLUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node filenamesSwapAssignerJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node filenamesSwapAssigner1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);

        Node filenamesReplaceAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Input, Literal), Node.Type.Void);
        Node filenamesReplaceAssigneeIndex = new Node(Node.NodeLabel.ObjectGetterCall, "[]", EnumSet.of(Variable, Input, Literal), Node.Type.String);
        Node filenamesReplaceAssigneeFilenames = new Node(Node.NodeLabel.Value, "filenames", EnumSet.of(Input), Node.Type.OtherClass);
        Node filenamesReplaceAssigneeIndexArgs = new Node(Node.NodeLabel.Arguments, null, EnumSet.of(Variable, Literal), null);
        Node filenamesReplaceAssigneePlus = new Node(Node.NodeLabel.ArithmeticOperator, "PLUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node filenamesReplaceAssigneeJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node filenamesReplaceAssignee1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);
        Node filenamesReplaceAssignerTemp = new Node(Node.NodeLabel.Value, "temp", EnumSet.of(Variable), Node.Type.String);

        Node jIncreaseAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Literal), Node.Type.Void);
        Node jIncreaseAssigneeJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node jIncreaseAssignerPlus = new Node(Node.NodeLabel.ArithmeticOperator, "PLUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node jIncreaseAssignerJ = new Node(Node.NodeLabel.Value, "j", EnumSet.of(Variable), Node.Type.Integer);
        Node jIncreaseAssigner1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);

        Node iDecreaseAssign = new Node(Node.NodeLabel.Assignment, "=", EnumSet.of(Variable, Literal), Node.Type.Void);
        Node iDecreaseAssigneeI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);
        Node iDecreaseAssignerMinus = new Node(Node.NodeLabel.ArithmeticOperator, "MINUS", EnumSet.of(Variable, Literal), Node.Type.Integer);
        Node iDecreaseAssignerI = new Node(Node.NodeLabel.Value, "i", EnumSet.of(Variable), Node.Type.Integer);
        Node iDecreaseAssigner1 = new Node(Node.NodeLabel.Value, "1", EnumSet.of(Literal), Node.Type.Integer);


        jIncreaseAssignerPlus.addChild(jIncreaseAssignerJ, Node.EdgeLabel.Operand);
        jIncreaseAssignerPlus.addChild(jIncreaseAssigner1, Node.EdgeLabel.Operand);
        jIncreaseAssign.addChild(jIncreaseAssignerPlus, Node.EdgeLabel.Assigner);
        jIncreaseAssign.addChild(jIncreaseAssigneeJ, Node.EdgeLabel.Assignee);

        iDecreaseAssignerMinus.addChild(iDecreaseAssignerI, Node.EdgeLabel.Operand);
        iDecreaseAssignerMinus.addChild(iDecreaseAssigner1, Node.EdgeLabel.Operand);
        iDecreaseAssign.addChild(iDecreaseAssignerMinus, Node.EdgeLabel.Assigner);
        iDecreaseAssign.addChild(iDecreaseAssigneeI, Node.EdgeLabel.Assignee);

        filenamesReplaceAssigneePlus.addChild(filenamesReplaceAssigneeJ, Node.EdgeLabel.Operand);
        filenamesReplaceAssigneePlus.addChild(filenamesReplaceAssignee1, Node.EdgeLabel.Operand);
        filenamesReplaceAssigneeIndexArgs.addChild(filenamesReplaceAssigneePlus, Node.EdgeLabel.MethodArgument);
        filenamesReplaceAssigneeIndex.addChild(filenamesReplaceAssigneeFilenames, Node.EdgeLabel.MethodCaller);
        filenamesReplaceAssigneeIndex.addChild(filenamesReplaceAssigneeIndexArgs, Node.EdgeLabel.MethodArguments);
        filenamesReplaceAssign.addChild(filenamesReplaceAssignerTemp, Node.EdgeLabel.Assigner);
        filenamesReplaceAssign.addChild(filenamesReplaceAssigneeIndex, Node.EdgeLabel.Assignee);

        filenamesSwapAssigneeIndexArgs.addChild(filenamesSwapAssigneeJ, Node.EdgeLabel.MethodArgument);
        filenamesSwapAssigneeIndex.addChild(filenamesSwapAssigneeFilenames, Node.EdgeLabel.MethodCaller);
        filenamesSwapAssigneeIndex.addChild(filenamesSwapAssigneeIndexArgs, Node.EdgeLabel.MethodArguments);
        filenamesSwapAssignerPlus.addChild(filenamesSwapAssignerJ, Node.EdgeLabel.Operand);
        filenamesSwapAssignerPlus.addChild(filenamesSwapAssigner1, Node.EdgeLabel.Operand);
        filenamesSwapAssignerIndexArgs.addChild(filenamesSwapAssignerPlus, Node.EdgeLabel.MethodArgument);
        filenamesSwapAssignerIndex.addChild(filenamesSwapAssignerFilenames, Node.EdgeLabel.MethodCaller);
        filenamesSwapAssignerIndex.addChild(filenamesSwapAssignerIndexArgs, Node.EdgeLabel.MethodArguments);
        filenamesSwapAssign.addChild(filenamesSwapAssignerIndex, Node.EdgeLabel.Assigner);
        filenamesSwapAssign.addChild(filenamesSwapAssigneeIndex, Node.EdgeLabel.Assignee);

        tempInitAssignerIndexArgs.addChild(tempInitAssignerJ, Node.EdgeLabel.MethodArgument);
        tempInitAssignerIndex.addChild(tempInitAssignerFilenames, Node.EdgeLabel.MethodCaller);
        tempInitAssignerIndex.addChild(tempInitAssignerIndexArgs, Node.EdgeLabel.MethodArguments);
        tempInitAssign.addChild(tempInitAssignerIndex, Node.EdgeLabel.Assigner);
        tempInitAssign.addChild(tempInitAssigneeTemp, Node.EdgeLabel.Assignee);

        innerIfBlock.addChild(tempInitAssign, Node.EdgeLabel.Statement);
        innerIfBlock.addChild(filenamesSwapAssign, Node.EdgeLabel.Statement);
        innerIfBlock.addChild(filenamesReplaceAssign, Node.EdgeLabel.Statement);

        innerIfCondCompareArgsPlus.addChild(innerIfCondCompareArgsJ, Node.EdgeLabel.Operand);
        innerIfCondCompareArgsPlus.addChild(innerIfCondCompareArgs1, Node.EdgeLabel.Operand);
        innerIfCondCompareArgsIndexArgs.addChild(innerIfCondCompareArgsPlus, Node.EdgeLabel.MethodArgument);
        innerIfCondCompareArgsIndex.addChild(innerIfCondCompareArgsFilenames, Node.EdgeLabel.MethodCaller);
        innerIfCondCompareArgsIndex.addChild(innerIfCondCompareArgsIndexArgs, Node.EdgeLabel.MethodArguments);
        innerIfCondCompareArgs.addChild(innerIfCondCompareArgsIndex, Node.EdgeLabel.MethodArgument);
        innerIfCondCompare.addChild(innerIfCondCompareIndex, Node.EdgeLabel.MethodCaller);
        innerIfCondCompare.addChild(innerIfCondCompareArgs, Node.EdgeLabel.MethodArguments);
        innerIfCondCompareIndexArgs.addChild(innerIfCondCompareJ, Node.EdgeLabel.MethodArgument);
        innerIfCondCompareIndex.addChild(innerIfCondCompareFilenames, Node.EdgeLabel.MethodCaller);
        innerIfCondCompareIndex.addChild(innerIfCondCompareIndexArgs, Node.EdgeLabel.MethodArguments);
        innerIfCond.addChild(innerIfCondCompare, Node.EdgeLabel.Operand);
        innerIfCond.addChild(innerIfCond0, Node.EdgeLabel.Operand);

        innerIf.addChild(innerIfCond, Node.EdgeLabel.Condition);
        innerIf.addChild(innerIfBlock, Node.EdgeLabel.Block);

        secondLoopCond.addChild(secondLoopCondJ, Node.EdgeLabel.Operand);
        secondLoopCond.addChild(secondLoopCondI, Node.EdgeLabel.Operand);

        secondLoopBlock.addChild(innerIf, Node.EdgeLabel.Statement);
        secondLoopBlock.addChild(jIncreaseAssign, Node.EdgeLabel.Statement);

        secondLoop.addChild(secondLoopCond, Node.EdgeLabel.Condition);
        secondLoop.addChild(secondLoopBlock, Node.EdgeLabel.Block);

        jInitAssign.addChild(jInitAssigner0, Node.EdgeLabel.Assigner);
        jInitAssign.addChild(jInitAssigneeJ, Node.EdgeLabel.Assignee);

        firstLoopCond.addChild(firstLoopCondI, Node.EdgeLabel.Operand);
        firstLoopCond.addChild(firstLoopCond0, Node.EdgeLabel.Operand);

        firstLoopBlock.addChild(jInitAssign, Node.EdgeLabel.Statement);
        firstLoopBlock.addChild(secondLoop, Node.EdgeLabel.Statement);
        firstLoopBlock.addChild(iDecreaseAssign, Node.EdgeLabel.Statement);

        firstLoop.addChild(firstLoopCond, Node.EdgeLabel.Condition);
        firstLoop.addChild(firstLoopBlock, Node.EdgeLabel.Block);

        iInitAssignerLength.addChild(iInitAssignerFilenames, Node.EdgeLabel.MethodCaller);
        iInitAssignerLength.addChild(iInitAssignerLengthArgs, Node.EdgeLabel.MethodArguments);
        iInitAssignerMinus.addChild(iInitAssignerLength, Node.EdgeLabel.Operand);
        iInitAssignerMinus.addChild(iInitAssigner1, Node.EdgeLabel.Operand);
        iInitAssign.addChild(iInitAssignerMinus, Node.EdgeLabel.Assigner);
        iInitAssign.addChild(iInitAssigneeI, Node.EdgeLabel.Assignee);

        method.addChild(iInitAssign, Node.EdgeLabel.Statement);
        method.addChild(firstLoop, Node.EdgeLabel.Statement);

        return method;
    }

}


//        should i use the score or the exp(score)?? (using score means somehow geometrical average)
//        iterative variable matching until convergance
//
//        Node.variableMatchings = varMatching;
//
//        // this should be ungreedy
//        List<SequenceDistanceCalculator.Matching<Node.ChildRelation>> pathMatching = new ArrayList<>();
//        while (true) {
//            double currentMin = Double.MAX_VALUE;
//            SequenceDistanceCalculator.Matching<Node.ChildRelation> currentMatching = null;
//            for (List<Node.ChildRelation> firstPath : firstPaths)
//                for (List<Node.ChildRelation> secondPath : secondPaths) {
//                    SequenceDistanceCalculator.Matching matching =
//                            SequenceDistanceCalculator.calculate(firstPath, secondPath, Node::getPenalty);
//                    if (pathMatching.stream().noneMatch(x -> x.seq1 == firstPath) && pathMatching.stream().noneMatch(x -> x.seq2 == secondPath) && matching.score < currentMin) {
//                        currentMin = matching.score;
//                        currentMatching = matching;
//                    }
//                }
//            if (currentMatching == null)
//                break;
//            pathMatching.add(currentMatching);
//        }
//
//        for (String firstVar : varMatching.keySet())
//            System.out.println(firstVar + ":" + varMatching.get(firstVar));
//
//        for (SequenceDistanceCalculator.Matching<Node.ChildRelation> matching : pathMatching)
//            System.out.println(matching);
