import AST.Node;
import Utils.Pair;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;

import java.util.HashMap;

public class GumtreeTest {
    public static void main(String[] args) {
//        CtClass<?> l1 = Launcher.parseClass("class Clazz {\n" +
//                "    private void main() {\n" +
//                "        if (i == j) {\n" +
//                "            i = func(j);\n" +
//                "            i = i + 2;\n" +
//                "            a = 2;\n" +
//                "        }\n" +
//                "    }\n" +
//                "}");
//        CtClass<?> l2 = Launcher.parseClass("class Clazz {\n" +
//                "    private void main() {\n" +
//                "        x = j == i;\n" +
//                "        if (x) {\n" +
//                "            i = i + 2;\n" +
//                "            a = 2;\n" +
//                "            i = func(j);\n" +
//                "        }\n" +
//                "    }\n" +
//                "}");
//
//        Diff diff = new AstComparator().compare(l1, l2);
//
//        int y = 1;

        Pair<Integer, Integer> a = new Pair<>(1, 2);
        Pair<Integer, Integer> b = new Pair<>(1, 2);
        Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> c = new Pair<>(a, b);

        Pair<Integer, Integer> a2 = new Pair<>(1, 2);
        Pair<Integer, Integer> b2 = new Pair<>(1, 2);
        Pair<Pair<Integer, Integer>, Pair<Integer, Integer>> c2 = new Pair<>(a, b);

        System.out.println(c.hashCode());
        System.out.println(c2.hashCode());
    }
}
