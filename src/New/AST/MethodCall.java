package New.AST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// i should have a function refernece here (like variable)
public class MethodCall extends Node {
    private String name;
    private Kind kind;
    private Type type;

    public Node caller;
    public ArgumentsBlock argumentsBlock;

    public MethodCall(String name, Kind kind, Type type, Node caller, ArgumentsBlock argumentsBlock) {
        this.kind = kind;
        this.name = name;
        this.type = type;
        this.caller = caller;
        this.argumentsBlock = argumentsBlock;
        setChildrenParent();
    }

    public String getName() {
        return name;
    }

    public Kind getKind() {
        return kind;
    }

    public Type getType() {
        return type;
    }

    public Node getCaller() {
        return caller;
    }

    public ArgumentsBlock getArgumentsBlock() {
        return argumentsBlock;
    }

    @Override
    public List<Child> getChildren() {
        if (caller == null)
            return Collections.singletonList(new Child(argumentsBlock, Role.MethodArgumentsBlock));
        return Arrays.asList(
                new Child(caller, Role.MethodCaller),
                new Child(argumentsBlock, Role.MethodArgumentsBlock));
    }

    @Override
    public boolean hasSameLocalVisualPattern(Node node) {
        return super.hasSameLocalVisualPattern(node) && ((MethodCall) node).name.equals(name);
    }

    @Override
    public List<Object> getLocalBehavioralPatternValues() {
        List<Object> result = new ArrayList<>(super.getLocalBehavioralPatternValues());
        result.addAll(List.of(name, kind, type));
        return result;
    }

    @Override
    public Summary getThisSummary() {
        return generateSummary(type, null, null);
    }

    @Override
    protected void toTextual(String linePrefix, List<Text> result) {
        if (caller != null)
            caller.toTextual(linePrefix + '\t', result);
        if ("[]".equals(name)) {
            result.add(new Text(this, "["));
            argumentsBlock.toTextual(linePrefix + '\t', result);
            result.add(new Text(this, "]"));
        } else {
            result.add(new Text(this, (caller == null ? "" : ".") + name + '('));
            argumentsBlock.toTextual(linePrefix + '\t', result);
            result.add(new Text(this, ")"));
        }
    }

    public enum Kind {
        ObjectSetter,
        ObjectGetter,
        ObjectMethod,
        ClassMethod
    }
}
