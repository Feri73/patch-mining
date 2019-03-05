package New.AST;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// node here is immutable (practically)
public abstract class Node {
    protected Node parent;

    // do not forget to call this in all children
    public void setChildrenParent() {
        for (Child child : getChildren()) {
            if (child.node.parent != null)
                throw new RuntimeException("node already has a parent!");
            child.node.parent = this;
        }
    }

    public Node getParent() {
        return parent;
    }

    public abstract List<Child> getChildren();

    public Child getChild(Node childNode) {
        return getChildren().stream().filter(x -> x.node == childNode).findAny().get();
    }

    protected Summary getThisSummary() {
        return generateSummary(null, null);
    }

    private Summary summary;

    public Summary getAggregatedSummary() {
        if (summary == null) {
            summary = Summary.aggregate(getChildren().stream()
                    .map(x -> x.node.getAggregatedSummary()).toArray(Summary[]::new));
            summary.add(getThisSummary());
        }
        return summary.copy();
    }

    protected Summary generateSummary(Type nodeType, Value.Source nodeSource) {
        return new Summary(getClass(), nodeType, nodeSource);
    }

    public List<Path> getPaths(Role role) {
        List<Path> paths = new ArrayList<>();

        List<Child> children = getChildren();

        for (Child child : children) {
            List<Path> elemPaths = child.node.getPaths(child.role);

            // PERFORMANCE
            Summary adjunctSummary = Summary.aggregate(children.stream().filter(x -> x != child)
                    .map(x -> x.node.getAggregatedSummary()).toArray(Summary[]::new));

            for (Path path : elemPaths) {
                path.add(0, new Path.Element(this, role, adjunctSummary));
                paths.add(path);
            }
        }

        if (paths.isEmpty()) {
            Path path = new Path();
            path.add(new Path.Element(Node.this, role, new Summary()));
            paths.add(path);
        }

        return paths;
    }

    // do not forget to override this
    public boolean hasSameLocalVisualPattern(Node node) {
        return node.getClass() == getClass();
    }

    // do not forget to override this
    public List<Object> getLocalBehavioralPatternValues(){
        return List.of(getClass());
    }

    // i should clean these classes to not have public fields

    public enum Role {
        Root,
        Condition,//both for if and elseif and loops
        Assignee,
        Assigner,
        MethodArgumentsBlock,
        MethodArgument,
        MethodCaller,
        Operand,
        Statement, // from only block
        Body // from if(and its else (but the penalty for else-then match should be 0)), loop, etc. to their body
    }

    public enum Type {
        Integer,
        Float,
        Boolean,
        String,
        JavaClass,
        OtherClass,
        Void,
        Array,
        Unknown;

        // use this in the penalty function
        public Type argument;
    }

    public static class Child {
        public Node node;
        public Role role;

        public Child(Node node, Role role) {
            this.node = node;
            this.role = role;
        }
    }

    public static class Summary {
        private Set<Class<? extends Node>> nodeClasses;
        private Set<Type> nodeTypes;
        private Set<Value.Source> nodeSources;

        public Summary() {
            nodeClasses = Set.of();
            nodeTypes = Set.of();
            nodeSources = Set.of();
        }

        public Summary(Class<? extends Node> nodeClass, Type nodeType, Value.Source nodeSource) {
            this();
            if (nodeClass != null)
                nodeClasses.add(nodeClass);
            if (nodeType != null)
                nodeTypes.add(nodeType);
            if (nodeSource != null)
                nodeSources.add(nodeSource);
        }

        public Summary copy() {
            Summary summary = new Summary();
            summary.add(this);
            return summary;
        }

        public void add(Summary summary) {
            nodeClasses.addAll(summary.nodeClasses);
            nodeTypes.addAll(summary.nodeTypes);
            nodeSources.addAll(summary.nodeSources);
        }

        public static Summary aggregate(Summary... summaries) {
            Summary result = new Summary();
            for (Summary summary : summaries)
                result.add(summary);
            return result;
        }
    }
}