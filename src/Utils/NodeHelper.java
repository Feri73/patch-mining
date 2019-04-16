package Utils;

import New.AST.Node;
import New.AST.Value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class NodeHelper {
    private NodeHelper() {
    }

    public static List<Node> getNodesBy(Node root, String text) {
        List<Node> result = new ArrayList<>();
        bfsOnNode(root,
                n -> {
                    if (n instanceof Value && ((Value) n).getText().equals(text))
                        result.add(n);
                    if (n.getClass().getName().contains('.' + text))
                        result.add(n);
                    return true;
                });
        return result;
    }

    public static void bfsOnNode(Node node, Function<Node, Boolean> function) {
        ArrayDeque<Node> nodeQueue = new ArrayDeque<>();
        nodeQueue.push(node);
        while (!nodeQueue.isEmpty()) {
            Node next = nodeQueue.pop();
            if (function.apply(next))
                for (Node.Child child : next.getChildren())
                    nodeQueue.push(child.node);
        }
    }
}
