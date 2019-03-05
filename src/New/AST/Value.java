package New.AST;

import java.util.ArrayList;
import java.util.List;

public class Value extends Node {
    private Type type;
    private String text;
    private Variable variable;

    public Value(Type type, String text) {
        this.type = type;
        this.text = text;
        variable = null;
        setChildrenParent();
    }

    public Value(Variable variable) {
        type = variable.getType();
        text = variable.getName();
        this.variable = variable;
        setChildrenParent();
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public Variable getVariable() {
        return variable;
    }

    public Source getSource() {
        if (variable == null)
            return Source.Literal;
        else if (variable.getKind() == Variable.Kind.Local)
            return Source.LocalVariable;
        else if (variable.getKind() == Variable.Kind.Input)
            return Source.Input;
        else
            throw new RuntimeException();
    }

    @Override
    public List<Child> getChildren() {
        return List.of();
    }

    @Override
    public boolean hasSameLocalVisualPattern(Node node) {
        return super.hasSameLocalVisualPattern(node) && ((Value) node).text == text;
    }

    @Override
    public List<Object> getLocalBehavioralPatternValues() {
        List<Object> result = new ArrayList<>(super.getLocalBehavioralPatternValues());
        if (variable == null)
            result.addAll(List.of(text, type));
        else
            result.add(variable);
        return result;
    }

    @Override
    protected Summary getThisSummary() {
        return generateSummary(type, getSource());
    }

    public enum Source {
        LocalVariable,
        Input,
        Literal
    }
}
