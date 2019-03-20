package New;

import New.AST.*;
import New.AST.SnippetConverter.Snippet;
import New.ActGen.ActionGenerator;
import New.ActGen.ActionGenerator.*;
import Utils.Average;
import Utils.BiMap;
import Utils.DefaultMap;
import Utils.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// search in code, everywhere i have copied a list or map (to return a field) and use unmodifiableList (or intellij suggestion in general) instead
// assumption: orderinals in actions are applied without any modification "ON BEF TREE", we can not assume any order of actions from actiongenerator (e.g. first delete, then reorder, ...)
// also another assumption is that the parent in actions is always present in the incrementally-built tree
// another assumption: there is no move action

// better aproach for ordinal, maintain a list of all nodes before the added node, and when adding the new node, put it
// in a place after all nodes in that list which are matched in the same parent

public class Patch {
    private final Snippet snippetBef;
    private final List<Action> actions;
    private final Map<Variable, Variable> aftBefVarMatch;

    private Patch(Snippet snippetBef, Snippet snippetAft) {
        this.snippetBef = snippetBef;

        BiMap<Variable, Variable, Double> varMatchScores =
                getVariableMatchScoresBetweenVersions(snippetBef, snippetAft);
        aftBefVarMatch = Program.getGreedyMatches(varMatchScores).stream()
                .collect(Collectors.toMap(Pair::getSecond, Pair::getFirst));

        actions = ActionGenerator.generate(snippetBef, snippetAft,
                Program.getGreedyMatches(Program.getNodeMatchScores(snippetBef, snippetAft, varMatchScores)));
    }

    public static Patch subtract(Snippet snippetBef, Snippet snippetAft) {
        return new Patch(snippetBef, snippetAft);
    }

    // this should not be public
    public static BiMap<Variable, Variable, Double>
    getVariableMatchScoresBetweenVersions(Snippet snippetBef, Snippet snippetAft) {
        return Program.getVariableMatcheScores(snippetBef, snippetAft,
                new BiMap<>((a, b) -> a.getName().equals(b.getName()) &&
                        a.getKind() == b.getKind() &&
                        a.getType() == b.getType() ? 0.0 : 1.0));
    }

    // test this by setting snippetNew = snippetBef
    public Node add(Snippet snippetNew) {
        return PatchGenerator.generate(snippetNew, snippetBef, actions, aftBefVarMatch).toImmutable();
    }

    // for now patch generator works in a deterministic way
    private static class PatchGenerator {
        private final Map<Node, Node> befNewNodeMatchMap;
        private final Map<Node, MutableNode> newMutableMap;
        private final MutableNode newMutableRoot;

        private final Map<Variable, Variable> aftBefVarMatch;

        private int befAftActionsCurrentIndex;
        private final List<Action> befAftActions;
        private boolean isDone;

        private final Map<Variable, Variable> befNewVarMatchMap;
        private final Map<PatternNode, List<Pair<Node, Double>>> subtreeBefNodeNewReplaceOccurances;
        private final Map<String, List<Pair<String, Double>>> methodNameBefNameNewReplacePatterns;

        // this method should be a yield-like method
        public static MutableNode generate(Snippet snippetNew, Snippet snippetBef,
                                           List<Action> befAftActions, Map<Variable, Variable> aftBefVarMatch) {
            Queue<Pair<PatchGenerator, Double>> patchGenerators =
                    new PriorityQueue<>(Comparator.comparing(x -> x.getSecond()));
            patchGenerators.add(new Pair<>(
                    new PatchGenerator(snippetNew, snippetBef, befAftActions, aftBefVarMatch), 0.0));

            while (!patchGenerators.isEmpty()) {
                PatchGenerator generator = patchGenerators.remove().getFirst();
                if (generator.isDone())
                    return generator.newMutableRoot;
                patchGenerators.addAll(generator.applyNextAction());
            }

            return null;
        }

        private PatchGenerator(Snippet snippetNew, Snippet snippetBef,
                               List<Action> befAftActions, Map<Variable, Variable> aftBefVarMatch) {
            BiMap<Variable, Variable, Double> varMatchScores =
                    Program.getVariableMatcheScores(snippetBef, snippetNew, null);
            BiMap<Node, Node, Double> befNewNodeMatchScores =
                    Program.getNodeMatchScores(snippetBef, snippetNew, varMatchScores);
            List<Pair<Node, Node>> befNewNodeMatches = Program.getGreedyMatches(befNewNodeMatchScores);

            befNewVarMatchMap = Program.getMatchMap(Program.getGreedyMatches(varMatchScores));
            befNewNodeMatchMap = Program.getMatchMap(befNewNodeMatches);

            subtreeBefNodeNewReplaceOccurances = getSubtreeBefNodeNewReplaceOccurances(snippetBef, snippetNew,
                    befNewNodeMatches, befNewVarMatchMap);
            // maybe if I need i go with a function class instead of name
            methodNameBefNameNewReplacePatterns = getMethodName1Name2ReplaceScores(befNewNodeMatchScores);

            newMutableMap = new HashMap<>();
            newMutableRoot = new MutableNode(snippetNew.getRoot(), newMutableMap);

            this.befAftActions = befAftActions;
            befAftActionsCurrentIndex = 0;
            isDone = false;

            this.aftBefVarMatch = aftBefVarMatch;
        }

        private PatchGenerator(PatchGenerator initializer) {
            befNewNodeMatchMap = initializer.befNewNodeMatchMap;
            newMutableMap = new HashMap<>();
            newMutableRoot = new MutableNode(initializer.newMutableRoot.associatedNode, newMutableMap);
            befNewVarMatchMap = initializer.befNewVarMatchMap;
            subtreeBefNodeNewReplaceOccurances = initializer.subtreeBefNodeNewReplaceOccurances;
            methodNameBefNameNewReplacePatterns = initializer.methodNameBefNameNewReplacePatterns;
            befAftActions = initializer.befAftActions;
            befAftActionsCurrentIndex = initializer.befAftActionsCurrentIndex;
            isDone = initializer.isDone;
            aftBefVarMatch = initializer.aftBefVarMatch;
        }

        private List<Pair<PatchGenerator, Double>> applyNextAction() {
            if (befAftActionsCurrentIndex == befAftActions.size() && !isDone) {
                isDone = true;
                // PERFORMANCE : becaue i do this for every patchGEnerator.
                convertMatchedAftVariablesInNewMutableNode(newMutableRoot);
                List<Pair<PatchGenerator, Double>> result = new ArrayList<>(applyNodeReplacePatterns());
                result.addAll(applyMethodNamePatterns());
                result.addAll(applyVariableNameMatches());
                return result;
            }

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
                            .selfAdd(new Average(1 / nodeMatchScore.getValue(), 1));

            Map<String, List<Pair<String, Double>>> result = new DefaultMap<>(x -> new ArrayList<>());
            for (BiMap.Entry<String, String, Average> methodNameReplaceScore : methodNameReplaceScores.getEntries())
                result.get(methodNameReplaceScore.getKey1())
                        .add(new Pair<>(methodNameReplaceScore.getKey2(),
                                1 / methodNameReplaceScore.getValue().getValue()));
            for (Map.Entry<String, List<Pair<String, Double>>> entry : result.entrySet())
                entry.getValue().sort(Comparator.comparing(x -> x.getSecond()));
            return result;
        }

        // this function is kina important
        // search for all literal integers i use in my code (like the .5 in this function),
        // and see if they are systematically chosen or ar based on my balls :|
        private static Map<PatternNode, List<Pair<Node, Double>>>
        getSubtreeBefNodeNewReplaceOccurances(Snippet snippetBef, Snippet snippetNew,
                                              List<Pair<Node, Node>> befNewNodeMatches,
                                              Map<Variable, Variable> befNewVarMatchMap) {
            // i should take care of literals here. because if a literal is added in the Aft program, without any match,
            // that should be added in to the New program without any replacement of anything else.
            BiMap<PatternNode, Node, Average> subtreeNodeReplaceOccurances = new BiMap<>((a, b) -> new Average());

            Predicate<Node> shouldBePattern = a ->
                    !(a instanceof Value)
                            || a instanceof Value
                            && ((Value) a).getVariable() != null
                            && !befNewVarMatchMap.containsKey(((Value) a).getVariable());

            // the scores in this function should be better (e.g. based on matching score,
            // like the scores in the function getMethodName1Name2ReplaceScores)

            // this is where I limit myself to type 2 clones
            // here is where i assume there is no move action, because i assume instead of move+add patterns
            // (search it) i have replace+add
            List<Action> befNewActions = ActionGenerator.generate(snippetBef, snippetNew, befNewNodeMatches);
            for (Action action : befNewActions)
                // search for all instanceof, and see if it is ok or should be getclass()==.class
                // should i use Rename here or not? (as you can see i do not use currently)
                if (action.getClass() == Replace.class) {
                    Replace replaceAction = (Replace) action;
                    // remove cases where a variable (not a value) is renamed to another variable
                    // the reason i say not a value is cases like this: var g in Bef has no match and is always replaced
                    // by literal 4 in New
                    if (shouldBePattern.test(replaceAction.getDeletedNode()))
                        subtreeNodeReplaceOccurances
                                .get(new PatternNode(replaceAction.getDeletedNode()), replaceAction.getNewNode())
                                .selfAdd(new Average(1, 1));
                }

            for (Pair<Node, Node> befNewMatch : befNewNodeMatches)
                if (shouldBePattern.test(befNewMatch.getFirst()))
                    subtreeNodeReplaceOccurances
                            .get(new PatternNode(befNewMatch.getFirst()), befNewMatch.getSecond())
                            .selfAdd(new Average(.5, 1));

            Map<PatternNode, List<Pair<Node, Double>>> result = new DefaultMap<>(a -> new ArrayList<>());
            for (BiMap.Entry<PatternNode, Node, Average> subtreeNodeReplaceOccurance :
                    subtreeNodeReplaceOccurances.getEntries())
                result.get(subtreeNodeReplaceOccurance.getKey1())
                        .add(new Pair<>(subtreeNodeReplaceOccurance.getKey2(),
                                // this is so that better values have loewr scores
                                1 / subtreeNodeReplaceOccurance.getValue().getValue()));
            for (Map.Entry<PatternNode, List<Pair<Node, Double>>> entry : result.entrySet())
                entry.getValue().sort(Comparator.comparing(x -> x.getSecond()));
            return result;
        }

        private MutableNode newMutable(Node node) {
            MutableNode result = newMutableMap.get(befNewNodeMatchMap.get(node));
            if (result == null)
                result = newMutableMap.get(node);
            if (result == null)
                result = new MutableNode(node, newMutableMap);
            return result;
        }

        private int convertEffectiveOrdinal(int effectiveOrdinal, Node befParent) {
            // in case befParent is not for bef (but for aft) this algorithm does not work perfectly and it is better to
            // work with the better aprpoach described on top of this file
            int result = effectiveOrdinal;
            if (!befNewNodeMatchMap.containsKey(befParent))
                return result;
            List<Node.Child> children = befParent.getChildren();
            for (int i = 0; i < children.size() && i < effectiveOrdinal; i++)
                if (!befNewNodeMatchMap.containsKey(children.get(i).node) ||
                        befNewNodeMatchMap.get(children.get(i).node).getParent() != befNewNodeMatchMap.get(befParent))
                    result--;
            return result;
        }

        // maybe i can move these to action, and make action use mutable node

        private List<Pair<PatchGenerator, Double>> applyMoveAction(Move befAftAction) {
            List<Pair<PatchGenerator, Double>> result = new ArrayList<>();
            result.add(new Pair<>(this, 1.0));

            MutableNode node = newMutable(befAftAction.getNode());

            if (node.getMutableParent() != null)
                node.delete();
            // PERFORMANCE
            MutableNode nextParent = newMutable(befAftAction.getParent());
//            if (nextParent != null)
            // this is because MutableNode constructor automatically adds
            // all children node as well (is it ok in all scnearios?)
            if (befNewNodeMatchMap.containsKey(nextParent.associatedNode))
                nextParent.addChild(node, befAftAction.getRole(),
                        convertEffectiveOrdinal(befAftAction.getEffectiveOrdinal(), befAftAction.getParent()));

            return result;
        }

        private List<Pair<PatchGenerator, Double>> applyReorderAction(Reorder befAftAction) {
            return applyMoveAction(befAftAction);
        }

        private List<Pair<PatchGenerator, Double>> applyAddAction(Add befAftAction) {
            List<Pair<PatchGenerator, Double>> result = new ArrayList<>();
            result.add(new Pair<>(this, 1.0));

            MutableNode nextParent = newMutable(befAftAction.getParent());
            // PERFORMANCE
//            if (nextParent != null)
            // this is because MutableNode constructor automatically adds
            // all children node as well (is it ok in all scnearios?)
            if (befNewNodeMatchMap.containsKey(nextParent.associatedNode))
                nextParent.addChild(befAftAction.getNode(), befAftAction.getRole(),
                        convertEffectiveOrdinal(befAftAction.getEffectiveOrdinal(), befAftAction.getParent()));

            return result;
        }

        private List<Pair<PatchGenerator, Double>> applyDeleteAction(Delete befAftAction) {
            List<Pair<PatchGenerator, Double>> result = new ArrayList<>();
            result.add(new Pair<>(this, 1.0));

            // PERFORMANCE
            MutableNode newDeletedNode = newMutable(befAftAction.getDeletedNode());
//            if (newDeletedNode != null)
            if (newDeletedNode.getMutableParent() != null)
                newDeletedNode.delete();

            return result;
        }

        private List<Pair<PatchGenerator, Double>> applyReplaceAction(Replace befAftAction) {
            List<Pair<PatchGenerator, Double>> result = new ArrayList<>();
            result.add(new Pair<>(this, 1.0));

            // PERFORMANCE
            MutableNode newDeletedNode = newMutable(befAftAction.getDeletedNode());
//            if (newDeletedNode == null)
//                return result;
            newDeletedNode.replace(befAftAction.getNewNode());

            return result;
        }

        private List<Pair<PatchGenerator, Double>> applyRenameAction(Rename befAftAction) {
            return List.of(new Pair<>(this, 1.0));
        }

        private List<Pair<PatchGenerator, Double>> applyNodeReplacePatterns() {
            bfsOnNewMutableNode(newMutableRoot, next -> {
                PatternNode nextPattern = new PatternNode(next);
                if (subtreeBefNodeNewReplaceOccurances.containsKey(nextPattern)) {
                    // note that because a pattern can be used more than once, a node can be associated to more than
                    // one mutable node and because of that, the association map is not valid anymore after this point
                    MutableNode toBeAdded = new MutableNode(
                            subtreeBefNodeNewReplaceOccurances.get(nextPattern).get(0).getFirst(), newMutableMap);
                    next.replace(toBeAdded);
                    convertMatchedAftVariablesInNewMutableNode(toBeAdded);
                    return false;
                }
                return true;
            });
            return List.of(new Pair<>(this, 1.0));
        }

        private List<Pair<PatchGenerator, Double>> applyMethodNamePatterns() {
            bfsOnNewMutableNode(newMutableRoot, next -> {
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
            return isDone;
        }

        private void convertMatchedAftVariablesInNewMutableNode(MutableNode newMutableNode) {
            bfsOnNewMutableNode(newMutableNode, next -> {
                if (next.associatedNode instanceof Value && ((Value) next.associatedNode).getVariable() != null) {
                    Variable nextVar = ((Value) next.associatedNode).getVariable();
                    if (aftBefVarMatch.containsKey(nextVar))
                        next.variable = aftBefVarMatch.get(nextVar);
                }
                return true;
            });
        }

        // this shoule be more complicated. if a variable exists in aft but not in bef, it should be retained in
        // newPatch. Otherwise, it should have not been added in the first place. So i should check this in add
        // and move actions.
        private List<Pair<PatchGenerator, Double>> applyVariableNameMatches() {
            bfsOnNewMutableNode(newMutableRoot, next ->
            {
                if (next.variable != null && befNewVarMatchMap.containsKey(next.variable))
                    next.variable = befNewVarMatchMap.get(next.variable);
                return true;
            });
            return List.of(new Pair<>(this, 1.0));
        }

        private void bfsOnNewMutableNode(MutableNode newMutableNode, Function<MutableNode, Boolean> function) {
            ArrayDeque<MutableNode> newMutableNodeQueue = new ArrayDeque<>();
            newMutableNodeQueue.push(newMutableNode);
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
            if (associatedNode instanceof MutableNode)
                throw new RuntimeException("Mutable node does not accept another mutable node.");
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
            MutableNode childNode = node instanceof MutableNode ? (MutableNode) node :
                    new MutableNode(node, associationMap);
            // is it ok?
            childrenMap.get(role).add(Math.min(ordinal, childrenMap.get(role).size()), childNode);
            childNode.parent = this;
        }

        public void deleteChild(MutableNode node) {
            for (Map.Entry<Role, List<MutableNode>> entry : childrenMap.entrySet())
                entry.getValue().remove(node);
            node.parent = null;
            removeFromAssociationMap(node);
        }

        private void removeFromAssociationMap(MutableNode node) {
            // PERFORMANCE
            for (Map.Entry<Node, MutableNode> entry : new HashSet<>(associationMap.entrySet()))
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

            MutableNode parent = getMutableParent();
            delete();
            parent.addChild(node, role, ordinal);
        }

        public MutableNode getMutableParent() {
            return (MutableNode) getParent();
        }

        @Override
        public List<Child> getChildren() {
            return childrenMap.entrySet().stream()
                    .collect(() -> new ArrayList<>(), (a, b) -> a.addAll(
                            b.getValue().stream().map(x -> new Child(x, b.getKey())).collect(Collectors.toList())),
                            (a, b) -> a.addAll(b));
        }

        @Override
        protected void toTextual(String linePrefix, List<Text> result) {

        }

        public Role getRole() {
            return getParent().getChild(this).role;
        }

        @Override
        public List<Object> getLocalBehavioralPatternValues() {
            // PERFORMANCE
            List<Object> result = associatedNode.getLocalBehavioralPatternValues();
            for (int i = 0; i < result.size(); i++)
                if (result.get(i) instanceof Variable)
                    result.set(i, variable);
            return result;
        }

        // assumption: there is no excessive children or roles
        public Node toImmutable() {
            if (associatedNode instanceof ArgumentsBlock)
                return new ArgumentsBlock(childrenMap.get(Role.MethodArgument).stream()
                        .map(MutableNode::toImmutable).collect(Collectors.toList()));
            if (associatedNode instanceof Operator) {
                Operator operator = (Operator) associatedNode;
                // assunption: 0 is left and 1 is right

                List<Node> operands = childrenMap.get(Role.Operand).stream()
                        .map(x -> x.toImmutable()).collect(Collectors.toList());
                if (associatedNode instanceof ArithmeticOperator)
                    return new ArithmeticOperator(operator.getName(), operator.getType(), operands);
                if (associatedNode instanceof BooleanOperator)
                    return new BooleanOperator(operator.getName(), operands);
                if (associatedNode instanceof CompareOperator)
                    return new CompareOperator(operator.getName(), operands);
            }
            if (associatedNode instanceof Assignment)
                return new Assignment(childrenMap.get(Role.Assigner).get(0).toImmutable(),
                        childrenMap.get(Role.Assignee).get(0).toImmutable());
            if (associatedNode instanceof Block)
                return new Block(childrenMap.get(Role.Statement).stream()
                        .map(MutableNode::toImmutable).collect(Collectors.toList()));
            if (associatedNode instanceof Branch)
                // here i am assuming then is the first child and else is the second child, which is not good.
                return new Branch(childrenMap.get(Role.Condition).get(0).toImmutable(),
                        (Block) childrenMap.get(Role.Body).get(0).toImmutable(),
                        childrenMap.get(Role.Body).size() == 1 ? null :
                                (Block) childrenMap.get(Role.Body).get(1).toImmutable());
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
                        childrenMap.get(Role.MethodCaller).isEmpty() ? null :
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
            throw new RuntimeException("Unsupported Type");
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
