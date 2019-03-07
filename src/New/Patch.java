package New;

import New.AST.*;
import New.ActGen.ActionGenerator;
import New.ActGen.ActionGenerator.Action;
import New.ActGen.ActionGenerator.Replace;
import New.ActGen.ActionGenerator.Add;
import New.ActGen.ActionGenerator.Delete;
import New.ActGen.ActionGenerator.Move;
import New.ActGen.ActionGenerator.Reorder;
import New.ActGen.ActionGenerator.Rename;
import New.AST.SnippetConverter.Snippet;
import Utils.Average;
import Utils.BiMap;
import Utils.DefaultMap;
import Utils.Pair;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// search in code, everywhere i have copied a list or map (to return a field) and use unmodifiableList (or intellij suggestion in general) instead
// assumption: orderinals in actions are applied without any modification "ON BEF TREE", we can not assume any order of actions from actiongenerator (e.g. first delete, then reorder, ...)
// also another assumption is that the parent in actions is always present in the incrementally-built tree

// better aproach for ordinal, maintain a list of all nodes before the added node, and when adding the new node, put it in a place after all nodes in that list which are matched in the same parent

public class Patch {
    private final Snippet snippetBef;
    private final List<Action> actions;

    private Patch(Snippet snippetBef, Snippet snippetAft) {
        this.snippetBef = snippetBef;
        actions = ActionGenerator.generate(snippetBef, snippetAft, Program.getNodeMatches(snippetBef, snippetAft));
    }

    public static Patch subtract(Snippet snippetBef, Snippet snippetAft) {
        return new Patch(snippetBef, snippetAft);
    }

    public Node add(Snippet snippetNew) {
        return PatchGenerator.generate(snippetNew, snippetBef, actions).toImmutable();
    }

    // for now patch generator works in a deterministic way
    private static class PatchGenerator {
        private final Map<Node, Node> befNewNodeMatchMap;
        private final Map<Node, MutableNode> newMutableMap;
        private final MutableNode newMutableRoot;

        private int befAftActionsCurrentIndex;
        private final List<Action> befAftActions;

        private final Map<Variable, Variable> befNewVarMatchMap;
        private final Map<PatternNode, List<Pair<Node, Integer>>> subtreeBefNodeNewReplaceOccurances;
        private final Map<String, List<Pair<String, Double>>> methodNameBefNameNewReplacePatterns;

        // this method should be a yield-like method
        public static MutableNode generate(Snippet snippetNew, Snippet snippetBef, List<Action> befAftActions) {
            Queue<PatchGenerator> patchGenerators = new PriorityQueue<>();
            patchGenerators.add(new PatchGenerator(snippetNew, snippetBef, befAftActions));

            while (!patchGenerators.isEmpty()) {
                PatchGenerator generator = patchGenerators.remove();
                generator.applyNextAction();
                if (generator.isDone())
                    return generator.newMutableRoot;
            }

            return null;
        }

        private PatchGenerator(Snippet snippetNew, Snippet snippetBef, List<Action> befAftActions) {
            BiMap<Variable, Variable, Double> varMatchScores = Program.getVariableMatcheScores(snippetBef, snippetNew);
            BiMap<Node, Node, Double> nodeMatchScores = Program.getNodeMatchScores(snippetBef, snippetNew, varMatchScores);
            List<Pair<Node, Node>> nodeMatches = Program.getGreedyMatches(nodeMatchScores);

            befNewVarMatchMap = Program.getMatchMap(Program.getGreedyMatches(varMatchScores));
            befNewNodeMatchMap = Program.getMatchMap(nodeMatches);

            subtreeBefNodeNewReplaceOccurances =
                    getSubtreeBefNodeNewReplaceOccurances(snippetBef, snippetNew, nodeMatches);
            // maybe if I need i go with a function class instead of name
            methodNameBefNameNewReplacePatterns = getMethodName1Name2ReplaceScores(nodeMatchScores);

            newMutableMap = new HashMap<>();
            newMutableRoot = new MutableNode(snippetNew.getRoot(), newMutableMap);

            this.befAftActions = befAftActions;
            befAftActionsCurrentIndex = 0;
        }

        private PatchGenerator(PatchGenerator initiator) {
            befNewNodeMatchMap = initiator.befNewNodeMatchMap;
            newMutableMap = new HashMap<>();
            newMutableRoot = new MutableNode(initiator.newMutableRoot.associatedNode, newMutableMap);
            befNewVarMatchMap = initiator.befNewVarMatchMap;
            subtreeBefNodeNewReplaceOccurances = initiator.subtreeBefNodeNewReplaceOccurances;
            methodNameBefNameNewReplacePatterns = initiator.methodNameBefNameNewReplacePatterns;
            befAftActions = initiator.befAftActions;
            befAftActionsCurrentIndex = initiator.befAftActionsCurrentIndex;
        }

        private List<Pair<PatchGenerator, Double>> applyNextAction() {
            Action action = befAftActions.get(befAftActionsCurrentIndex);
            befAftActionsCurrentIndex++;
            if (action instanceof Rename)
                return applyRenameAction((Rename) action);
            if (action instanceof Replace)
                return applyReplaceAction((Replace) action);
            if (action instanceof Delete)
                return applyDeleteAction((Delete) action);
            if (action instanceof Add)
                return applyAddAction((Add) action);
            if (action instanceof Reorder)
                return applyReorderAction((Reorder) action);
            if (action instanceof Move)
                return applyMoveAction((Move) action);
            else throw new RuntimeException("unsupported action");
        }

        private static Map<String, List<Pair<String, Double>>>
        getMethodName1Name2ReplaceScores(BiMap<Node, Node, Double> node1Node2MatchScores) {
            BiMap<String, String, Average> methodNameReplaceScores = new BiMap<>((a, b) -> new Average());

            for (BiMap.Entry<Node, Node, Double> nodeMatchScore : node1Node2MatchScores.getEntries())
                if (nodeMatchScore.getKey1() instanceof MethodCall && nodeMatchScore.getKey2() instanceof MethodCall)
                    methodNameReplaceScores.get(((MethodCall) nodeMatchScore.getKey1()).getName(),
                            ((MethodCall) nodeMatchScore.getKey2()).getName())
                            .selfAdd(new Average(nodeMatchScore.getValue(), 1));

            Map<String, List<Pair<String, Double>>> result = new DefaultMap<>(x -> new ArrayList<>());
            for (BiMap.Entry<String, String, Average> methodNameReplaceScore : methodNameReplaceScores.getEntries())
                result.get(methodNameReplaceScore.getKey1())
                        .add(new Pair<>(methodNameReplaceScore.getKey2(),
                                methodNameReplaceScore.getValue().getValue()));
            for (Map.Entry<String, List<Pair<String, Double>>> entry : result.entrySet())
                entry.getValue().sort(Comparator.comparing(x -> x.getSecond()));
            return result;
        }

        private static Map<PatternNode, List<Pair<Node, Integer>>>
        getSubtreeBefNodeNewReplaceOccurances(Snippet snippetBef, Snippet snippetNew,
                                              List<Pair<Node, Node>> nodeMatches) {
            BiMap<PatternNode, Node, Average> subtreeNodeReplaceOccurances = new BiMap<>((a, b) -> new Average());

            // this is where I limit myself to type 2 clones
            List<Action> befNewActions = ActionGenerator.generate(snippetBef, snippetNew, nodeMatches);
            for (Replace replaceAction :
                    befNewActions.stream().filter(x -> x instanceof Replace)
                            .map(x -> (Replace) x).collect(Collectors.toList()))
                subtreeNodeReplaceOccurances
                        .get(new PatternNode(replaceAction.getDeletedNode()), replaceAction.getNewNode())
                        .selfAdd(new Average(1, 1));

            Map<PatternNode, List<Pair<Node, Integer>>> result = new HashMap<>();
            for (BiMap.Entry<PatternNode, Node, Average> subtreeNodeReplaceOccurance :
                    subtreeNodeReplaceOccurances.getEntries())
                result.get(subtreeNodeReplaceOccurance.getKey1())
                        .add(new Pair<>(subtreeNodeReplaceOccurance.getKey2(),
                                (int) subtreeNodeReplaceOccurance.getValue().getValue()));
            for (Map.Entry<PatternNode, List<Pair<Node, Integer>>> entry : result.entrySet())
                entry.getValue().sort(Comparator.comparing(x -> x.getSecond()));
            return result;
        }

        private MutableNode newMutable(Node node) {
            MutableNode result = newMutableMap.get(befNewNodeMatchMap.get(node));
            if (result == null)
                result = newMutableMap.get(node);
            return result;
        }

        private int convertEffectiveOrdinal(int effectiveOrdinal, Node befParent) {
            List<Node.Child> children = befParent.getChildren();
            for (int i = 0; i < children.size() && i < effectiveOrdinal; i++)
                if (!befNewNodeMatchMap.containsKey(children.get(i).node) ||
                        befNewNodeMatchMap.get(children.get(i).node).getParent() != befNewNodeMatchMap.get(befParent))
                    effectiveOrdinal--;
            return effectiveOrdinal;
        }

        // maybe i can move these to action, and make action use mutable node

        private List<Pair<PatchGenerator, Double>> applyMoveAction(Move befAftAction) {
            List<Pair<PatchGenerator, Double>> result = new ArrayList<>();
            result.add(new Pair<>(this, 1.0));

            MutableNode node = newMutable(befAftAction.getNode());
            if (node == null)
                return result;
            node.delete();
            MutableNode nextParent = newMutable(befAftAction.getParent());
            if (nextParent != null)
                nextParent.addChild(node, befAftAction.getRole(), befAftAction.getEffectiveOrdinal());

            return result;
        }

        private List<Pair<PatchGenerator, Double>> applyReorderAction(Reorder befAftAction) {
            return applyMoveAction(befAftAction);
        }

        private List<Pair<PatchGenerator, Double>> applyAddAction(Add befAftAction) {
            List<Pair<PatchGenerator, Double>> result = new ArrayList<>();
            result.add(new Pair<>(this, 1.0));

            MutableNode nextParent = newMutable(befAftAction.getParent());
            if (nextParent != null)
                nextParent.addChild(befAftAction.getNode(), befAftAction.getRole(), befAftAction.getEffectiveOrdinal());

            return result;
        }

        private List<Pair<PatchGenerator, Double>> applyDeleteAction(Delete befAftAction) {
            List<Pair<PatchGenerator, Double>> result = new ArrayList<>();
            result.add(new Pair<>(this, 1.0));

            MutableNode newDeletedNode = newMutable(befAftAction.getDeletedNode());
            if (newDeletedNode != null)
                newDeletedNode.delete();

            return result;
        }

        private List<Pair<PatchGenerator, Double>> applyReplaceAction(Replace befAftAction) {
            List<Pair<PatchGenerator, Double>> result = new ArrayList<>();
            result.add(new Pair<>(this, 1.0));

            MutableNode newDeletedNode = newMutable(befAftAction.getDeletedNode());
            if (newDeletedNode == null)
                return result;

            newDeletedNode.replace(befAftAction.getNewNode());

            return result;
        }

        private List<Pair<PatchGenerator, Double>> applyRenameAction(Rename befAftAction) {
            return List.of(new Pair<>(this, 1.0));
        }

        private List<Pair<PatchGenerator, Double>> applyNodeReplacePatterns() {
            bfsOnNewMutable(next -> {
                PatternNode nextPattern = new PatternNode(next.associatedNode);
                if (subtreeBefNodeNewReplaceOccurances.containsKey(nextPattern)) {
                    // note that because a pattern can be used more than once, a node can be associated to more than
                    // one mutable node and because of that, the association map is not valid anymore after this point
                    next.replace(subtreeBefNodeNewReplaceOccurances.get(nextPattern).get(0).getFirst());
                    return false;
                }
                return true;
            });
            return List.of(new Pair<>(this, 1.0));
        }

        private List<Pair<PatchGenerator, Double>> applyMethodNamePatterns() {
            bfsOnNewMutable(next -> {
                if (next.associatedNode instanceof MethodCall) {
                    String nextName = ((MethodCall) next.associatedNode).getName();
                    if (methodNameBefNameNewReplacePatterns.containsKey(nextName))
                        next.methodName = methodNameBefNameNewReplacePatterns.get(nextName).get(0).getFirst();
                }
                return true;
            });
            return List.of(new Pair<>(this, 1.0));
        }

        private boolean isDone() {
            return befAftActionsCurrentIndex == befAftActions.size();
        }

        // this shoule be more complicated. if a variable exists in aft but not in bef, it should be retained in newPatch.
        // Otherwise, it should have not been added in the first place. So i should check this in add and move actions
        private List<Pair<PatchGenerator, Double>> applyVariableNameMatches() {
            bfsOnNewMutable(next ->
            {
                if (next.associatedNode instanceof Value || ((Value) next.associatedNode).getVariable() == null) {
                    Variable nextVar = ((Value) next.associatedNode).getVariable();
                    if (befNewVarMatchMap.containsKey(nextVar))
                        next.variable = befNewVarMatchMap.get(nextVar);
                }
                return true;
            });
            return List.of(new Pair<>(this, 1.0));
        }

        private void bfsOnNewMutable(Function<MutableNode, Boolean> function) {
            ArrayDeque<MutableNode> newMutableNodeQueue = new ArrayDeque<>();
            newMutableNodeQueue.push(newMutableRoot);
            while (!newMutableNodeQueue.isEmpty()) {
                MutableNode next = newMutableNodeQueue.pop();
                if (function.apply(next))
                    for (Node.Child child : next.getChildren())
                        newMutableNodeQueue.push((MutableNode) child.node);
            }
        }

    }

    private static class MutableNode extends Node {
        private final Node associatedNode;
        private final DefaultMap<Role, List<MutableNode>> childrenMap;
        private final Map<Node, MutableNode> associationMap;

        // assumption: I hearby assume that there aint no change to literal values
        private String methodName;
        private Variable variable;

        MutableNode(Node associatedNode, Map<Node, MutableNode> associationMap) {
            this.associatedNode = associatedNode;
            this.associationMap = associationMap;
            associationMap.put(associatedNode, this);

            childrenMap = new DefaultMap<>(x -> new ArrayList<>());
            childrenMap.putAll(associatedNode.getChildren().stream()
                    .collect(Collectors.groupingBy(x -> x.role,
                            Collectors.mapping(x -> new MutableNode(x.node, associationMap), Collectors.toList()))));

            methodName = null;
            variable = null;

            setChildrenParent();
        }

        public void addChild(Node node, Role role, int ordinal) {
            childrenMap.get(role).add(ordinal, new MutableNode(node, associationMap));
            setChildrenParent();
        }

        public void deleteChild(MutableNode node) {
            for (Map.Entry<Role, List<MutableNode>> entry : childrenMap.entrySet())
                entry.getValue().remove(node);
            node.parent = null;
            removeFromAssociationMap(node);
        }

        private void removeFromAssociationMap(MutableNode node) {
            for (Map.Entry<Node, MutableNode> entry : associationMap.entrySet())
                if (entry.getValue() == node)
                    // PERFORMANCE : ADD A BREAK
                    associationMap.remove(entry.getKey());
        }

        public void delete() {
            getMutableParent().deleteChild(this);
        }

        public void replace(Node node) {
            Role role = getRole();

            int ordinal = 0;
            List<MutableNode> siblings = getMutableParent().childrenMap.get(role);
            for (int i = 0; i < siblings.size(); i++)
                if (siblings.get(i) == this) {
                    ordinal = i;
                    break;
                }

            delete();
            addChild(node, role, ordinal);
        }

        public MutableNode getMutableParent() {
            return (MutableNode) getParent();
        }

        @Override
        public List<Child> getChildren() {
            return childrenMap.entrySet().stream()
                    .collect(() -> new ArrayList<>(), (a, b) -> a.addAll(
                            b.getValue().stream().map(x -> new Child(x, b.getKey())).collect(Collectors.toList())),
                            null);
        }

        public Role getRole() {
            return getParent().getChild(this).role;
        }

        // assumption: there is no excessive children or roles
        public Node toImmutable() {
            if (associatedNode instanceof ArgumentsBlock)
                return new ArgumentsBlock(childrenMap.get(Role.MethodArgument).stream()
                        .map(MutableNode::toImmutable).collect(Collectors.toList()));
            if (associatedNode instanceof Operator) {
                Operator operator = (Operator) associatedNode;
                // assunption: 0 is left and 1 is right
                int leftIndex;
                if (childrenMap.get(Role.Operand).get(0).associatedNode == operator.getLefOperand())
                    leftIndex = 0;
                else
                    leftIndex = 1;
                try {
                    return (Node) associatedNode.getClass().getConstructors()[0]
                            .newInstance(childrenMap.get(Role.Operand).get(leftIndex).toImmutable(),
                                    childrenMap.get(Role.Operand).get(1 - leftIndex).toImmutable());
                } catch (InstantiationException | InvocationTargetException | IllegalAccessException ignored) {
                }
            }
            if (associatedNode instanceof Assignment)
                return new Assignment(childrenMap.get(Role.Assigner).get(0).toImmutable(),
                        childrenMap.get(Role.Assignee).get(0).toImmutable());
            if (associatedNode instanceof Block)
                return new Block(childrenMap.get(Role.Statement).stream()
                        .map(MutableNode::toImmutable).collect(Collectors.toList()));
            if (associatedNode instanceof Branch) {
                Branch branch = (Branch) associatedNode;
                int thenIndex;
                if (childrenMap.get(Role.Body).get(0).associatedNode == branch.getThenBody())
                    thenIndex = 0;
                else
                    thenIndex = 1;
                return new Branch(childrenMap.get(Role.Condition).get(0),
                        (Block) childrenMap.get(Role.Body).get(thenIndex).toImmutable(),
                        (Block) childrenMap.get(Role.Body).get(1 - thenIndex).toImmutable());
            }
            if (associatedNode instanceof Break)
                return new Break();
            if (associatedNode instanceof Continue)
                return new Continue();
            if (associatedNode instanceof Loop)
                return new Loop(childrenMap.get(Role.Condition).get(0).toImmutable(),
                        (Block) childrenMap.get(Role.Body).get(0).toImmutable(), ((Loop) associatedNode).getKind());
            if (associatedNode instanceof MethodCall) {
                MethodCall methodCall = (MethodCall) associatedNode;
                String name = methodName == null ? methodCall.getName() : methodName;
                return new MethodCall(name, methodCall.getKind(), methodCall.getType(),
                        childrenMap.get(Role.MethodCaller).get(0).toImmutable(),
                        (ArgumentsBlock) childrenMap.get(Role.MethodArgumentsBlock).get(0).toImmutable());
            }
            if (associatedNode instanceof Value) {
                Value value = (Value) associatedNode;
                Variable var = variable == null ? value.getVariable() : variable;
                if (var == null)
                    return new Value(value.getType(), value.getText());
                return new Value(var);
            }
            throw new RuntimeException("Unsupprted Type");
        }
    }

    private static class PatternNode {
        private final Node node;
        private final List<PatternNode> children;

        PatternNode(Node node) {
            this.node = node;
            children = node.getChildren().stream().map(x -> new PatternNode(x.node)).collect(Collectors.toList());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PatternNode) || hashCode() != obj.hashCode())
                return false;
            PatternNode pattern = (PatternNode) obj;
            return node.getLocalBehavioralPatternValues().equals(pattern.node.getLocalBehavioralPatternValues())
                    && children.equals(pattern.children);
        }

        private int hashCode = -1;

        @Override
        public int hashCode() {
            if (hashCode == -1) {
                List<Object> hashables = node.getLocalBehavioralPatternValues();
                hashables.addAll(children);
                hashCode = Objects.hashCode(hashables);
            }
            return hashCode;
        }
    }
}
