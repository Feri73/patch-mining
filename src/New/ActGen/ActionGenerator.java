package New.ActGen;

import New.AST.Node;
import New.AST.SnippetConverter.Snippet;
import New.Program;
import Utils.DefaultMap;
import Utils.Pair;

import java.util.*;
import java.util.stream.Collectors;

// better naming and packaging for all classes
// what if we have one deletedNode to multiple deletedNode matching?
// IMPORTANT: ANCHOR SHOULD NOT BE ONLY THE PARENT. CONSIDER THIS: P1:{A=2;X==A;} PP1:{A=2+R;X==A;} P2:{X==2} HERE BECAUSE THERE IS NO = IN P2 WE NEVER ADD +R IF WE ONLY CONSIDER PARENT AS ANCHOR
public class ActionGenerator {

    private final Map<Node, Boolean> isBeforeNode;
    private final List<Pair<Node, Node>> nodeMatches;
    private final Map<Node, Node> nodeMatchMap;

    private Map<Node, Boolean> aftIsProcessed;

    private ActionGenerator(Snippet snippetBef, Snippet snippetAft, List<Pair<Node, Node>> nodeMatches) {
        this.nodeMatches = new ArrayList<>(nodeMatches);
        nodeMatchMap = Program.getMatchMap(nodeMatches);

        isBeforeNode = new HashMap<>();
        isBeforeNode.putAll(getAllNodes(snippetBef).stream().collect(Collectors.toMap(x -> x, x -> true)));
        isBeforeNode.putAll(getAllNodes(snippetAft).stream().collect(Collectors.toMap(x -> x, x -> false)));
    }

    // PERFORMANCE
    private static Set<Node> getAllNodes(Snippet snippet) {
        Set<Node> result = new HashSet<>();

        ArrayDeque<Node> nodeQueue = new ArrayDeque<>();
        nodeQueue.push(snippet.getRoot());
        while (!nodeQueue.isEmpty()) {
            Node node = nodeQueue.pop();
            result.add(node);
            for (Node.Child child : node.getChildren())
                nodeQueue.push(child.node);
        }

        return result;
    }

    private void addCopy(Node nodeAft, List<Action> result) {
        Pair<Node.Role, Integer> effectiveOridnalAft = getEffectiveOrdinalInParent(nodeAft);

        for (Action action : result)
            if (action instanceof Delete) {
                Delete deleteAction = (Delete) action;
                Pair<Node.Role, Integer> effectiveOridnalBef = getEffectiveOrdinalInParent(deleteAction.deletedNode);
                if (getMatch(nodeAft.getParent()) == deleteAction.deletedNode.getParent() &&
                        effectiveOridnalAft.equals(effectiveOridnalBef)) {
                    result.remove(deleteAction);
                    result.add(new Replace(deleteAction.deletedNode, nodeAft));
                    return;
                }
            }

        Node parent = nodeAft.getParent();
        if (isMatched(nodeAft.getParent()))
            parent = getMatch(nodeAft.getParent());

        Pair<Node.Role, Integer> effectiveOrdinal = getEffectiveOrdinalInParent(nodeAft);
        result.add(new Add(nodeAft, parent, effectiveOrdinal.getFirst(), effectiveOrdinal.getSecond()));

        aftIsProcessed.put(nodeAft, true);
    }

    private void addDelete(Node nodeBef, List<Action> result) {
        Pair<Node.Role, Integer> effectiveOridnalBef = getEffectiveOrdinalInParent(nodeBef);

        for (Action action : result)
            if (action instanceof Add) {
                Add addAction = (Add) action;
                Pair<Node.Role, Integer> effectiveOridnalAft = getEffectiveOrdinalInParent(addAction.getNode());
                if (getMatch(nodeBef.getParent()) == addAction.getNode().getParent() &&
                        effectiveOridnalAft.equals(effectiveOridnalBef)) {
                    result.remove(addAction);
                    result.add(new Replace(nodeBef, addAction.getNode()));
                    return;
                }
            }

        result.add(new Delete(nodeBef));
    }

    private boolean isMatched(Node node) {
        return nodeMatchMap.containsKey(node);
    }

    private Node getMatch(Node node) {
        return nodeMatchMap.get(node);
    }

    private Pair<Node.Role, Integer> getEffectiveOrdinalInParent(Node node) {
        DefaultMap<Node.Role, List<Node>> siblingsMap = new DefaultMap<>(x -> new ArrayList<>());
        Node.Role nodeRole = null;
        for (Node.Child child : node.getParent().getChildren())
            if (child.node == node) {
                nodeRole = child.role;
                break;
            } else if (isMatched(child.node) && getMatch(child.node).getParent() == getMatch(child.node.getParent())
                    || !isBeforeNode.get(child.node) && aftIsProcessed.get(child.node))
                siblingsMap.get(child.role).add(child.node);
        return new Pair<>(nodeRole, siblingsMap.get(nodeRole).size() - 1);
    }

    private static Node.Role getRoleInParent(Node node) {
        return node.getParent().getChild(node).role;
    }

    private void addReorder(Node nodeBef, Node nodeAft, List<Action> result) {
        // this is not optmial
        Pair<Node.Role, Integer> effectiveOrdinalBef = getEffectiveOrdinalInParent(nodeBef);
        Pair<Node.Role, Integer> effectiveOrdinalAft = getEffectiveOrdinalInParent(nodeAft);

        if (effectiveOrdinalBef.getSecond() > effectiveOrdinalAft.getSecond())
            result.add(new Reorder(nodeBef, nodeBef.getParent(), effectiveOrdinalAft.getSecond()));
    }

    private void createMove(Node nodeBef, Node nodeAft, Node parent, List<Action> result) {
        Pair<Node.Role, Integer> effectiveOrdinal = getEffectiveOrdinalInParent(nodeAft);
        result.add(new Move(nodeBef, parent, effectiveOrdinal.getFirst(), effectiveOrdinal.getSecond()));
        aftIsProcessed.put(nodeAft, true);
    }

    // to test this, compare generate(p,q) with reverse(generate(q,p))
    // reconsider this method overally
    private List<Action> generate() {
        aftIsProcessed = new DefaultMap<>(a -> false);

        List<Action> result = new ArrayList<>();

        // PERFORMANCE
        for (Node node : nodeMatchMap.keySet())
            if (isBeforeNode.get(node))
                addDelete(node, result);

        for (Pair<Node, Node> nodeMatch : nodeMatches) {
            Node nodeBef = nodeMatch.getFirst();
            Node nodeAft = nodeMatch.getSecond();

            if (isMatched(nodeAft.getParent()) && getMatch(nodeAft.getParent()) == nodeBef.getParent())
                addReorder(nodeBef, nodeAft, result);
            if (!nodeAft.hasSameLocalVisualPattern(nodeBef))
                result.add(new Rename(nodeBef, nodeAft));
        }

        for (Pair<Node, Node> nodeMatch : nodeMatches) {
            Node nodeBef = nodeMatch.getFirst();
            Node nodeAft = nodeMatch.getSecond();

            if (isMatched(nodeAft.getParent())) {
                if (getMatch(nodeAft.getParent()) != nodeBef.getParent())
                    createMove(nodeBef, nodeAft, getMatch(nodeAft.getParent()), result);
            } else
                createMove(nodeBef, nodeAft, getMatch(nodeAft.getParent()), result);
        }

        for (Node node : nodeMatchMap.keySet())
            if (!isBeforeNode.get(node))
                addCopy(node, result);

        return result;
    }

    public static List<Action> generate(Snippet snippetBef, Snippet snippetAft, List<Pair<Node, Node>> nodeMatches) {
        return new ActionGenerator(snippetBef, snippetAft, nodeMatches).generate();
    }

    public interface Action {
    }

    public static class Move implements Action {
        private final Node node;
        private final Node parent;
        private final Node.Role role;
        private final int effectiveOrdinal;

        public Move(Node node, Node parent, Node.Role role, int effectiveOrdinal) {
            this.node = node;
            this.parent = parent;
            this.role = role;
            this.effectiveOrdinal = effectiveOrdinal;
        }

        public Node getNode() {
            return node;
        }

        public Node getParent() {
            return parent;
        }

        public Node.Role getRole() {
            return role;
        }

        public int getEffectiveOrdinal() {
            return effectiveOrdinal;
        }
    }

    public static class Reorder extends Move {
        public Reorder(Node node, Node parent, int effectiveOrdinal) {
            super(node, parent, getRoleInParent(node), effectiveOrdinal);
        }
    }

    public static class Add extends Move {
        public Add(Node node, Node parent, Node.Role role, int effectiveOrdinal) {
            super(node, parent, role, effectiveOrdinal);
        }
    }

    public static class Delete implements Action {
        private final Node deletedNode;

        public Delete(Node deletedNode) {
            this.deletedNode = deletedNode;
        }

        public Node getDeletedNode() {
            return deletedNode;
        }
    }

    public static class Replace extends Delete {
        private final Node newNode;

        public Replace(Node node, Node newNode) {
            super(node);
            this.newNode = newNode;
        }

        public Node getNewNode() {
            return newNode;
        }
    }

    // the difference between replace and rename is that in rename, nodes are matched and in replace, are not
    // in the behavioral aspect, in replace, the deletedNode is removed and the other is adde, while in rename, the values in the in new deletedNode are replaced
    public static class Rename extends Replace {
        public Rename(Node node, Node newNode) {
            super(node, newNode);
        }
    }
}