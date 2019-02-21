package ActGen;

import AST.Node;

import java.util.*;
import java.util.stream.Collectors;

// what if we have one node to multiple node matching?
// IMPORTANT: ANCHOR SHOULD NOT BE ONLY THE PARENT. CONSIDER THIS: P1:{A=2;X==A;} PP1:{A=2+R;X==A;} P2:{X==2} HERE BECAUSE THERE IS NO = IN P2 WE NEVER ADD +R IF WE ONLY CONSIDER PARENT AS ANCHOR
public class ActionGenerator {
    private static Node.EdgeLabel getEdgeLabel(Node node) {
        // here i do not take care if parent is null
        return node.parent.children.stream().filter(x -> x.child == node).findAny().get().edgeLabel;
    }

    private static void addAdd(Node node, List<Action> result, Map<Node, Node> reverseNodeMatching) {
        boolean flag = false;
        for (Action action : result)
            if (action instanceof Delete && reverseNodeMatching.get(node.parent) == ((Delete) action).fromAnchor) {
                result.remove(action);
                result.add(new Update(((Delete) action).relation, new Node.ChildRelation(node, getEdgeLabel(node)),
                        ((Delete) action).fromAnchor));
                flag = true;
                break;
            }
        if (!flag) {
            List<Node> sib1 = node.parent.children.stream().map(x -> x.child).collect(Collectors.toList());
            int count1 = 0;
            for (Node sib : sib1) {
                if (reverseNodeMatching.containsKey(sib) && reverseNodeMatching.get(sib).parent == reverseNodeMatching.get(sib.parent))
                    count1++;
                if (sib == node)
                    break;
            }
            result.add(new Add(new Node.ChildRelation(node, getEdgeLabel(node)), reverseNodeMatching.get(node.parent), count1));
        }
    }

    private static void addDelete(Node node, List<Action> result, Map<Node, Node> nodeMatching) {
        boolean flag = false;
        for (Action action : result)
            // in these cases (same as above method also, search for others as well) not only the parent should be same but the children should be in the same index of the same category of children
            if (action instanceof Add && node.parent == ((Add) action).toAnchor) {
                result.remove(action);
                result.add(new Update(new Node.ChildRelation(node, getEdgeLabel(node)), ((Add) action).relation,
                        ((Add) action).toAnchor));
                flag = true;
                break;
            }
        if (!flag) {
            List<Node> sib1 = node.parent.children.stream().map(x -> x.child).collect(Collectors.toList());
            int count1 = 0;
            for (Node sib : sib1)
                if (nodeMatching.containsKey(sib) && nodeMatching.get(sib).parent == nodeMatching.get(sib.parent)) {
                    count1++;
                    if (sib == node)
                        break;
                }
            result.add(new Delete(new Node.ChildRelation(node, getEdgeLabel(node)), node.parent, count1));
        }
    }

    // to test this, compare generate(p,q) with reverse(generate(q,p))
    public static List<Action> generate(Node r1, Node r2, Map<Node, Node> nodeMatching) {
        List<Action> result = new ArrayList<>();

        Map<Node, Node> reverseNodeMatching = nodeMatching.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        ArrayDeque<Node> allNodes1 = new ArrayDeque<>();
        allNodes1.push(r1);
        while (!allNodes1.isEmpty()) {
            Node node1 = allNodes1.pop();
            if (!nodeMatching.containsKey(node1) && nodeMatching.containsKey(node1.parent))
                addDelete(node1, result, nodeMatching);
            for (Node.ChildRelation rel : node1.children)
                allNodes1.push(rel.child);
        }

        ArrayDeque<Node> allNodes2 = new ArrayDeque<>();
        allNodes2.push(r2);
        while (!allNodes2.isEmpty()) {
            Node node2 = allNodes2.pop();
            if (!reverseNodeMatching.containsKey(node2) && reverseNodeMatching.containsKey(node2.parent))
                addAdd(node2, result, reverseNodeMatching);
            for (Node.ChildRelation rel : node2.children)
                allNodes2.push(rel.child);
        }

        for (Node matchedNode1 : nodeMatching.keySet()) {
            if (reverseNodeMatching.containsKey(nodeMatching.get(matchedNode1).parent)) {
                if (reverseNodeMatching.get(nodeMatching.get(matchedNode1).parent) != matchedNode1.parent) {
//                    result.add(new Move(new Node.ChildRelation(matchedNode1, getEdgeLabel(matchedNode1))
//                            , matchedNode1.parent, reverseNodeMatching.get(nodeMatching.get(matchedNode1).parent)));
                    addDelete(matchedNode1, result, nodeMatching);
                    addAdd(nodeMatching.get(matchedNode1), result, reverseNodeMatching);
                }
                // test this
                else {
                    // this is not optmial
                    List<Node> sib1 = matchedNode1.parent.children.stream().map(x -> x.child).collect(Collectors.toList());
                    List<Node> sib2 = nodeMatching.get(matchedNode1).parent.children.stream().map(x -> x.child).collect(Collectors.toList());
                    int count1 = 0;
                    for (Node sib : sib1)
                        if (nodeMatching.containsKey(sib) && nodeMatching.get(sib).parent == nodeMatching.get(sib.parent)) {
                            count1++;
                            if (sib == matchedNode1)
                                break;
                        }
                    int count2 = 0;
                    for (Node sib : sib2)
                        if (reverseNodeMatching.containsKey(sib) && reverseNodeMatching.containsKey(sib.parent) && reverseNodeMatching.get(sib).parent == reverseNodeMatching.get(sib.parent)) {
                            count2++;
                            if (sib == nodeMatching.get(matchedNode1))
                                break;
                        }
                    if (count1 > count2)
                        result.add(new Reorder(new Node.ChildRelation(matchedNode1, getEdgeLabel(matchedNode1)),
                                matchedNode1.parent, count1 - 1, count2 - 1));
                }
            } else if (matchedNode1.parent != null && nodeMatching.get(matchedNode1).parent != null) {
                // these are the only actions that have anchor in the second program space
//                result.add(new Move(new Node.ChildRelation(matchedNode1, getEdgeLabel(matchedNode1)),
//                        matchedNode1.parent, nodeMatching.get(matchedNode1).parent));
                addDelete(matchedNode1, result, nodeMatching);
            }
            if (matchedNode1.value != null && !matchedNode1.value.equals(nodeMatching.get(matchedNode1).value))
                result.add(new Rename(matchedNode1, nodeMatching.get(matchedNode1)));
        }

        return result;
    }

    public static List<Action> reverse(List<Action> actions) {
        List<Action> result = new ArrayList<>();
        for (int i = actions.size() - 1; i >= 0; i--)
            result.add(actions.get(i).getReversed());
        return result;
    }


    public interface Action {
        Action getReversed();
    }

    public static class Move implements Action {
        public Node.ChildRelation relation;
        public Node fromAnchor;
        public Node toAnchor;

        public Move(Node.ChildRelation relation, Node fromAnchor, Node toAnchor) {
            this.relation = relation;
            this.fromAnchor = fromAnchor;
            this.toAnchor = toAnchor;
        }

        @Override
        public String toString() {
            return "node " + relation.child + ":\n\t" + fromAnchor + "----move---->" + toAnchor;
        }

        @Override
        public Action getReversed() {
            return new Move(relation, toAnchor, fromAnchor);
        }
    }

    public static class Reorder extends Move {

        public int fromIndex;
        public int toIndex;

        public Reorder(Node.ChildRelation relation, Node anchor, int fromIndex, int toIndex) {
            super(relation, anchor, anchor);
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }

        @Override
        public String toString() {
            return "node " + relation.child + " in " + fromAnchor + ":\n\t" + fromIndex + "----reorder---->" + toIndex;
        }

        @Override
        public Action getReversed() {
            return new Reorder(relation, fromAnchor, toIndex, fromIndex);
        }
    }

    // add should indicate the index (in children of anchor) the node is added
    public static class Add implements Action {
        public Node.ChildRelation relation;
        public Node toAnchor;
        public int childIndex;

        public Add(Node.ChildRelation relation, Node toAnchor, int childIndex) {
            this.relation = relation;
            this.toAnchor = toAnchor;
            this.childIndex = childIndex;
        }

        @Override
        public String toString() {
            return "node " + relation.child + ":\n\t" + "----add---->" + toAnchor + ":" + childIndex;
        }

        @Override
        public Action getReversed() {
            return new Delete(relation, toAnchor, childIndex);
        }
    }

    public static class Delete implements Action {
        public Node.ChildRelation relation;
        public Node fromAnchor;
        public int childIndex;

        public Delete(Node.ChildRelation relation, Node fromAnchor, int childIndex) {
            this.relation = relation;
            this.fromAnchor = fromAnchor;
            this.childIndex = childIndex;
        }

        @Override
        public String toString() {
            return "node " + relation.child + ":\n\t" + fromAnchor + ":" + childIndex + "----delete---->";
        }

        @Override
        public Action getReversed() {
            return new Add(relation, fromAnchor, childIndex);
        }
    }

    public static class Update implements Action {
        public Node.ChildRelation deletedRelation;
        public Node.ChildRelation addedRelation;
        public Node anchor;

        public Update(Node.ChildRelation deletedRelation, Node.ChildRelation addedRelation, Node anchor) {
            this.deletedRelation = deletedRelation;
            this.addedRelation = addedRelation;
            this.anchor = anchor;
            if("1".equals(deletedRelation.child.value)){
                int t=1;
            }
        }

        @Override
        public String toString() {
            return "node " + deletedRelation + "----updated-to---->" + addedRelation.child;
        }

        @Override
        public Action getReversed() {
            return new Update(addedRelation, deletedRelation, anchor);
        }
    }

    public static class Rename implements Action {
        public Node formerNode;
        public Node currentNode;

        public Rename(Node formerNode, Node currentNode) {
            this.formerNode = formerNode;
            this.currentNode = currentNode;
        }

        @Override
        public String toString() {
            return "node " + formerNode + "----renamed-to---->" + currentNode;
        }

        @Override
        public Action getReversed() {
            return new Rename(currentNode, formerNode);
        }
    }
}
