package AST;

import Utils.StyledPrinter;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// test all functions separately


// maybe i should ignore the field access stuff -> e.g. for a.b use it as Node.Lable.Values with value="a.b" (at least for inputs)

// we can consider this whole thing as the sequence alignment of two sequences of vectors. This way, it can be customized by choosinfg different vector parameters and penalty functions, and the research in this field would be how to choose these.


// do the experiments without this class and with spoon structure (because it will be accepted easier (maybe, do and check the results))

// include validation for each node (e.g. operator should have valuesource and type)
public class Node {

    // use this in getPenalty :|
    // maybe instead of this I can have the score for matching every two variable and use that in getPenalty(in this case, i may want to divide the score for matching every two variable(computed using GetVarVarMap) by the length of matching or sequences or ... (think about it))
    public static HashMap<String, String> variableMatchings;
    public static boolean diffMode = false;

    // defined better features (e.g. no i do not have any good feature representing what a block does)
    public Type type;
    public NodeLabel label;
    // i can also cluster variable names, function names, etc. (generally value of nodes), and in addition to the raw value, use their cluster (such as indexingNames(i,j), etc.) fior alignment etc.
    public String value;
    public EnumSet<ValueSource> sources;
    public List<ChildRelation> children = new ArrayList<>();
    public Node parent;

    public String styleCode = "";

    // dont forget to change i++ and i-- and i*=sth, i+=sth, etc. to their formal format  (i=i+1, etc.)
    public Node(NodeLabel label, String value) {
        this(label, value, EnumSet.noneOf(ValueSource.class), null);
    }

    public Node(NodeLabel label, String value, EnumSet<ValueSource> sources, Type type) {
        this.label = label;
        this.value = value;
        this.type = type;
        this.sources = sources;
    }

    public void addChild(Node node, EdgeLabel edgeLabel) {
        children.add(new ChildRelation(node, edgeLabel));
        node.parent = this;
    }

    @Override
    public String toString() {
        return label + ":" + type + ":" + value + ":" + sources;
    }

    public List<Node> getChildren(EdgeLabel edgeLabel) {
        return children.stream().filter(x -> x.edgeLabel == edgeLabel).map(x -> x.child).collect(Collectors.toList());
    }

    public int count() {
        int res = 0;
        for (ChildRelation relation : children)
            res += relation.child.count();
        return res + 1;
    }

    public void applyOnAll(Consumer<Node> func) {
        func.accept(this);
        for (ChildRelation childRelation : children)
            childRelation.child.applyOnAll(func);
    }

    private String styled(String txt) {
        return StyledPrinter.applyStyle(styleCode, txt);
    }

    public String toStyledString(String linePrefix) {
        switch (label) {
            case Branch:
                String res = linePrefix + styled(value + "(") + getChildren(EdgeLabel.Condition).get(0).toStyledString("") + styled(")\n") +
                        getChildren(EdgeLabel.Block).get(0).toStyledString(linePrefix);
                if (getChildren(EdgeLabel.Block).size() > 1)
                    res += styled("\n") + linePrefix + styled("else") + styled("\n") +
                            getChildren(EdgeLabel.Block).get(1).toStyledString(linePrefix);
                return res;
            case Loop:
                return linePrefix + styled(value + "(") + getChildren(EdgeLabel.Condition).get(0).toStyledString("") + styled(")\n") +
                        getChildren(EdgeLabel.Block).get(0).toStyledString(linePrefix);
            case Block:
                return linePrefix + styled("{\n") +
                        getChildren(EdgeLabel.Statement).stream().map(x -> x.toStyledString(linePrefix + "\t"))
                                .collect(StringBuilder::new,
                                        (response, element) -> response.append(element + styled(";\n")),
                                        (response1, response2) -> response1.append(response2 + styled(";\n"))) +
                        styled("\n") + linePrefix + styled("}");
            case Value:
                return linePrefix + styled(value);
            case Assignment:
                return linePrefix + getChildren(EdgeLabel.Assignee).get(0).toStyledString("") + styled(" " + value + " ") + getChildren(EdgeLabel.Assigner).get(0).toStyledString("");
            case ObjectSetterCall:
            case ObjectGetterCall:
            case ObjectMethodCall:
                if (getChildren(EdgeLabel.MethodCaller).size() == 0)
                    return linePrefix + styled(value) + getChildren(EdgeLabel.MethodArguments).get(0).toStyledString("");
                return linePrefix + getChildren(EdgeLabel.MethodCaller).get(0).toStyledString("") + styled("." + value) + getChildren(EdgeLabel.MethodArguments).get(0).toStyledString("");
            case ClassMethodCall:
                return linePrefix + styled(value) + getChildren(EdgeLabel.MethodArguments).get(0).toStyledString("");
            case CompareOperator:
            case BooleanOperator:
            case ArithmeticOperator:
                if (getChildren(EdgeLabel.Operand).size() == 1)
                    return linePrefix + styled(value + " ") + getChildren(EdgeLabel.Operand).get(0).toStyledString("");
                return linePrefix + getChildren(EdgeLabel.Operand).get(0).toStyledString("") + styled(" " + value + " ") + getChildren(EdgeLabel.Operand).get(1).toStyledString("");
            case Break:
                return linePrefix + styled("break");
            case Continue:
                return linePrefix + styled("continue");
            case Arguments:
                return linePrefix + styled("(") + getChildren(EdgeLabel.MethodArgument).stream().map(x -> x.toStyledString(""))
                        .collect(StringBuilder::new,
                                (response, element) -> response.append(styled(",")).append(element),
                                (response1, response2) -> response1.append(styled(",")).append(response2))
                        + styled(")");
            default:
                throw new UnsupportedOperationException();
        }
    }

    // test this
    private EnumMap<ValueSource, Integer> aggregate() {
        EnumMap<ValueSource, Integer> sourcesCounts = new EnumMap<>(ValueSource.class);
        for (ChildRelation relation : children)
            //  do we need this?
            if (relation.edgeLabel != EdgeLabel.Statement)
                for (ValueSource source : relation.child.sources)
                    sourcesCounts.put(source, sourcesCounts.getOrDefault(source, 0) + 1);
        return sourcesCounts;
    }

    // test this
    public List<List<PathElement>> getAllPaths() {
        List<List<PathElement>> paths = new ArrayList<>();

        EnumMap<ValueSource, Integer> sourcesCounts = aggregate();

        for (ChildRelation relation : children) {
            EnumSet<ValueSource> sources = EnumSet.noneOf(ValueSource.class);
            for (ValueSource source : sourcesCounts.keySet())
                if (sourcesCounts.get(source) - (relation.child.sources.contains(source) ? 1 : 0) > 0)
                    sources.add(source);

            List<List<PathElement>> childPaths = relation.child.getAllPaths();
            for (List<PathElement> path : childPaths)
                path.add(0, new PathElement(this, relation.edgeLabel, sources));
            paths.addAll(childPaths);
        }
        if (paths.isEmpty())
            paths.add(new ArrayList<>() {
                private static final long serialVersionUID = -6555689788506184131L;

                {
                    // i used to put valueSources=sources. but now i do not because valueSources belongs to the subtree that is not in the path.
                    add(new PathElement(Node.this, null, EnumSet.noneOf(ValueSource.class)));
                }
            });
        return paths;
    }

    // test it
    public HashMap<Node, List<PathElement>> getLeadingPaths() {
        HashMap<Node, List<PathElement>> result = new HashMap<>();

        EnumMap<ValueSource, Integer> sourcesCounts = aggregate();

        for (ChildRelation relation : children) {
            EnumSet<ValueSource> sources = EnumSet.noneOf(ValueSource.class);
            for (ValueSource source : sourcesCounts.keySet())
                if (sourcesCounts.get(source) - (relation.child.sources.contains(source) ? 1 : 0) > 0)
                    sources.add(source);

            HashMap<Node, List<PathElement>> leadingPaths = relation.child.getLeadingPaths();
            for (Node node : leadingPaths.keySet()) {
                leadingPaths.get(node).add(0, new PathElement(this, relation.edgeLabel, sources));
                result.put(node, leadingPaths.get(node));
            }
        }

        result.put(this, new ArrayList<>() {

            private static final long serialVersionUID = -5583912680427139109L;

            {
                // i used to put valueSources=sources. but now i do not because valueSources belongs to the subtree that is not in the path.
                add(new PathElement(Node.this, null, EnumSet.noneOf(ValueSource.class)));
            }
        });

        return result;
    }

    // how can i create (learn) this function automatically? (or at least iterate on them and find the best values)
    // it is possible to make this function very complicated, e.g. ot be dependent on the context as well (e.g. if a var and input ar eto be matched in the condition of a branch, the penalty would be different)
    // another possibility is to use this function to affect the currently known matchings (e.g. from past iteration) (but not from current alignments, as this makes the calculator suboptimal)
    // the const of skiping two labels should be equal to the cost of matching them
    // how the templatability of different concepts (such as operators, functions, types, etc.) affects this function. (like how now we have templatabe var names and therefore do not affect their names in the penalty). This means that e.g. if we want to generalize to all functions that sort, regardless of weather it is string or int, how different would we treat different types when matching variables. It also may affect the enums instead.
    // this function should be symmetric (does it?)
    // this is the core of my method. think about its properties and how to make it optimal.
    // if i can immitate this sequence matching (with custom penalty func (with isactually a hypercube)) with a math formula, I can learn this hyper cube using sgd.
    // should this be probability based or what? (write complete mathematical formula)
    // one nice thing that i can do to make this really simple is to identify a set of completely independent ad orthogonal dimensions for the features of nodes (e.g. a node can be both value and arithmetic operator, so in the feature vector, these dimensions are separated and a "-" node has 1 for both of those dimensions) and use simple vector arithmatic to compute this function (as an another example, = is both an assignment and an operator, + is only an operator, "if" is none of them)
    // more complicated logic needed for type comparison
    public static double getPenalty(PathElement elem1, PathElement elem2) {
        double res = 1;

        if (elem1 == null) {
            PathElement tmp = elem1;
            elem1 = elem2;
            elem2 = tmp;
        }

        if (elem2 != null && elem1.node.label.ordinal() > elem2.node.label.ordinal()) {
            PathElement tmp = elem1;
            elem1 = elem2;
            elem2 = tmp;
        }

        switch (elem1.node.label) {
            case Branch:
                if (elem2 == null)
                    res *= .5;
                else switch (elem2.node.label) {
                    case Loop:
                        res *= .1;
                        break;
                    case Block:
                    case Value:
                    case Assignment:
                        res = 0;
                        break;
                    case ObjectSetterCall:
                    case ObjectGetterCall:
                    case ObjectMethodCall:
                    case ClassMethodCall:
                        res *= .001;
                        break;
                    case CompareOperator:
                    case BooleanOperator:
                    case ArithmeticOperator:
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case Loop:
                if (elem2 == null)
                    res *= .2;
                else switch (elem2.node.label) {
                    case Block:
                    case Value:
                    case Assignment:
                        res = 0;
                        break;
                    case ObjectSetterCall:
                    case ObjectGetterCall:
                    case ObjectMethodCall:
                    case ClassMethodCall:
                        res *= .002;
                        break;
                    case CompareOperator:
                    case BooleanOperator:
                    case ArithmeticOperator:
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case Block:
                if (elem2 != null && elem2.node.label != NodeLabel.Block)
                    res = 0;
                break;
            case Value:// this can be affeced by the type,source,etc. of the nodes
                if (elem2 == null)
                    res *= .3;
                else switch (elem2.node.label) {
                    case Assignment:
                        res *= .05;
                        break;
                    case ObjectSetterCall:
                        res *= .005;
                        break;
                    case ObjectGetterCall:
                        res *= .5;
                        break;
                    case ObjectMethodCall:
                    case ClassMethodCall:
                        res *= .3;
                        break;
                    case CompareOperator:
                    case BooleanOperator:
                    case ArithmeticOperator:
                        res *= .3;
                        break;
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case Assignment:
                if (elem2 == null)
                    res *= .1;
                else switch (elem2.node.label) {
                    case ObjectSetterCall:
                        res *= .5;
                        break;
                    case ObjectGetterCall:
                        res = 0;
                        break;
                    case ObjectMethodCall:
                        res *= .1;
                        break;
                    case ClassMethodCall:
                        res *= .09;
                        break;
                    case CompareOperator:
                    case BooleanOperator:
                    case ArithmeticOperator:
                        res *= .05;
                        break;
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case ObjectSetterCall:
                if (elem2 == null)
                    res *= .01;
                else switch (elem2.node.label) {
                    case ObjectGetterCall:
                        res = 0;
                        break;
                    case ObjectMethodCall:
                        res *= .05;
                        break;
                    case ClassMethodCall:
                        res *= .001;
                        break;
                    case CompareOperator:
                    case BooleanOperator:
                    case ArithmeticOperator:
                        res *= .05;
                        break;
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case ObjectGetterCall:
                if (elem2 == null)
                    res *= .05;
                else switch (elem2.node.label) {
                    case ObjectMethodCall:
                        res *= .4;
                        break;
                    case ClassMethodCall:
                        res *= .1;
                        break;
                    case CompareOperator:
                    case BooleanOperator:
                    case ArithmeticOperator:
                        res *= .1;
                        break;
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case ObjectMethodCall:
                if (elem2 == null)
                    res *= .05;
                else switch (elem2.node.label) {
                    case ClassMethodCall:
                        res *= .8;
                        break;
                    case CompareOperator:
                    case BooleanOperator:
                    case ArithmeticOperator:
                        res *= .8;
                        break;
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case ClassMethodCall:
                if (elem2 == null)
                    res *= .2;
                else switch (elem2.node.label) {
                    case CompareOperator:
                    case BooleanOperator:
                    case ArithmeticOperator:
                        res *= .8;
                        break;
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case CompareOperator:
                if (elem2 == null)
                    res *= .2;
                else switch (elem2.node.label) {
                    case BooleanOperator:
                    case ArithmeticOperator:
                        res *= .4;
                        break;
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case BooleanOperator:
                if (elem2 == null)
                    res *= .2;
                else switch (elem2.node.label) {
                    case ArithmeticOperator:
                        res *= .4;
                        break;
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case ArithmeticOperator:
                if (elem2 == null)
                    res *= .2;
                else switch (elem2.node.label) {
                    case Break:
                    case Continue:
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case Break:
                if (elem2 == null)
                    res *= .03;
                else switch (elem2.node.label) {
                    case Continue:
                        res *= .5;
                        break;
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case Continue:
                if (elem2 == null)
                    res *= .03;
                else switch (elem2.node.label) {
                    case Arguments:
                        res = 0;
                        break;
                }
                break;
            case Arguments:
                break;
        }
        // remember! this project serves as two purposes. first, to see if they programs do same thing (if the inconcsistenties is low). second, to tell the difference between them (report inconsistensies AND other diffs). if variable's names (and anything that is a hole in template (by our definition of template)) is not the same it is not inconsistency (but it is difference). but if a function call changes from this() to that() (and function call is not in template a hole) then it is inconsistency.

        if (elem2 != null) {
            // more complicated logic needed here
            if (elem1.childEdgeLabel != elem2.childEdgeLabel)
                res *= .1;
            if (elem1.node.type != elem2.node.type)
                res *= .9;
            // should i keep it or not? (i think i should keep it and selfAdd more info to it, or keep it in clone detection and remove it in diff detection)
            res *= sourcesSimilarity(elem1.node.sources, elem2.node.sources) * .5 + .5; // this creates path-long correlation between elements matching score because we have both the in-the-path node source (next element) and the off-the-path sources (see the next line) which together create this. BUT solving it is not simple because a variable may be matched to an operator and in this case te node.sources of the two should be compared (and note, variable does not have valueSources because it is a leaf)
            res *= sourcesSimilarity(elem1.valueSources, elem2.valueSources) * .6 + .4;

            // should i check values of other kinds of labels?

            // this should be scrutinized and checked carefully
            if (diffMode && elem1.node.value != null && !elem1.node.value.equals(elem2.node.value))
                res *= .3;

            // i should explicityly check that elem1 and elem2 are valid (variable and input)
            if (variableMatchings != null && elem1.node.label == NodeLabel.Value && elem2.node.label == NodeLabel.Value
                    && (!elem1.node.sources.equals(EnumSet.of(ValueSource.Literal))
                    || !elem2.node.sources.equals(EnumSet.of(ValueSource.Literal))))
                if (variableMatchings.getOrDefault(elem1.node.value, "").equals(elem2.node.value))
                    // this is important. because now i use "node"edgelabel, here childedgelabel is null and does not affect this. but if i use edgelabel (of the very node) then i should check the labels after this line because two "i"s if(i) and i=4 shoud not be matched with score 1!
                    res = 1;
                else
                    res *= .01; // is it good or should it be calculated?
        }

        return -Math.log(res);
    }

    private static double sourcesSimilarity(EnumSet<ValueSource> src1, EnumSet<ValueSource> src2) {
        // uncomment these two lines after completing getPenalty function
//        if (src1.isEmpty() || src2.isEmpty()) // to prevent correlation
//            return 1;
        if (src1.isEmpty() && src2.isEmpty())
            return 1;
        EnumSet<ValueSource> intersection = EnumSet.copyOf(src1);
        EnumSet<ValueSource> union = EnumSet.copyOf(src1);
        intersection.retainAll(src2);
        union.addAll(src2);
        return (double) intersection.size() / union.size();
    }

    // test this
    public boolean subtreeEquals(Node p2) {
        if (!(label == p2.label && (value == null && p2.value == null || value.equals(p2.value)) && type == type && sources.containsAll(p2.sources) && p2.sources.containsAll(sources) && children.size() == p2.children.size()))
            return false;
        for (int i = 0; i < children.size(); i++)
            if (children.get(i).edgeLabel != p2.children.get(i).edgeLabel || !children.get(i).child.subtreeEquals(p2.children.get(i).child))
                return false;
        return true;
    }

    public void replaceSubtree(Node patternNode, Node newNode) {
        for (ChildRelation relation : children) {
            if (relation.child.subtreeEquals(patternNode))
                relation.child = newNode;
            else
                relation.child.replaceSubtree(patternNode, newNode);
        }
    }

    // make each one a different class
    // shared super class for variable,literal,&input - boolean&arithmetic, etc.
    // do the method calls and operators need type?
    // maybe input,variable,and literal should be in another dimension because e.g. a+b is a arithmetic, and also is either input or variable
    // variable decleration?
    // a[c]=2, b=2
    // == and eq are onething
    // maybe make loop node have a condition and a body? (now it only has a body (some children (statement)))
    // selfAdd castings
    // now, e.g. after every mehod call i know i have an argument, so maybe i can do soemthing for it to optimize the alignment algorithm (IF those method calls match, the next arguments MUST match)
    // other optimizations, sometings such as block or arg (or et.c search for it) either do not match (skip) or match exactly with the same label. it helps optimizing the algorithm a lot.
    // consider try catch throw assert as well (but have in mind that penalty of removing them is really low)
    // lambda support??
    // this (keyword)
    public enum NodeLabel {
        // maybe if, for, etc. should also have source (now the source for these is null but e.g. source for function call or assignment is not null, why there is such a difference (both for and an assignemtn are added to block with statement role)?)
        Branch,//including ?: and switch case - branch has else and elseif(does it?)
        Loop,//loop is while(condition). the i++(for for) would be separated. this includes foeach
        //do i really need block?
        Block,//matching this to null is cost free
        Value,
        Assignment,//its type is void (is it good?), a=b=c --> becomes a=c and b=c
        ObjectSetterCall,//methods startinng with set
        ObjectGetterCall,//including indexing, property access, and methods starting with get etc.
        ObjectMethodCall,
        ClassMethodCall,//including property access,constructor call, (if first letter of the called obj is capital, we assume it is a class), here there is no method caller involved and so the valueSources will be that of arguments??
        CompareOperator,
        BooleanOperator,
        ArithmeticOperator, // the arithmetics always return int (no!), boolean and compare always boolean, so here occurs a duplication (however, simply combining them to a binary operator wont work for 1. we have unary operators too, 2.compar and boolean have same return type but are semantically different)
        Break, // should i make loop be conditionless and selfAdd a branch (and break) inside it?
        Continue, // should i selfAdd a continue at the end of all loops?
        Arguments // matching this to null is cost free, this label has variable sources, if there is no argument this does not have no children and its valueSources should be empty and be ignored in the comparison (so be careful: it is not the case that the last node in a path is always a value)
    }

    // maybe these should recursively be computed for every node (as well as every edge aggregation) when creating paths. remember that for some relationships (like block or if-statement) these do not go up (in a path, if-confition node does not have this aggregation) (but think aboutother features that can be goinh up)
    public enum ValueSource {
        Variable,
        Input,
        Literal
    }

    // sharded super class for int,boolean,etc. as primary types
    // do we need to have an unkown type (are there primitive-typed vars that we cannot understand their types (or even its primitiveness)?) read diff papers and stuff to see how they handle typing
    public enum Type {
        Integer, // remember to consider both Integer and int types
        Float, //including double, remember other classes such as Float
        Boolean,
        String,
        JavaClass, //including enums
        OtherClass, // including enums
        Void, // currently i say void is the type of assignment. but for for, while etc. i use null. be consistent ffs :|
        Array, // how to encode the type of array
        Unknown;

        public Type argument;
    }

    // remove the label for nodes that only have one type of label for their children (operator and operant)
    // better name: role!
    public enum EdgeLabel {
        Condition,//both for if and elseif and loops
        Assignee,
        Assigner,
        MethodArguments,
        MethodArgument,
        MethodCaller,
        Operand,
        Statement, // from only block
        Block // from if(and its else (but the penalty for else-then match should be 0)), loop, etc. to their body
    }

    public static class PathElement {
        public Node node;
        // this should be fromParentEdgeLabel (or we should include both?)
        public EdgeLabel childEdgeLabel;
        // aggregated feature
        public EnumSet<ValueSource> valueSources;

        public PathElement(Node node, EdgeLabel childEdgeLabel, EnumSet<ValueSource> valueSources) {
            this.node = node;
            this.childEdgeLabel = childEdgeLabel;
            this.valueSources = valueSources;
        }

        @Override
        public String toString() {
            return "" + node + "---" + childEdgeLabel + "," + valueSources + "--->";
        }
    }

    public static class ChildRelation {
        public Node child;
        public EdgeLabel edgeLabel;

        public ChildRelation(Node child, EdgeLabel edgeLabel) {
            this.child = child;
            this.edgeLabel = edgeLabel;
        }

        @Override
        public String toString() {
            return "(" + edgeLabel + "," + child + ")";
        }
    }
}
