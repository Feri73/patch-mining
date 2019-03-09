package New.AST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class Operator extends Node {
    private String name;
    private Type type;

    private Node lefOperand;
    private Node rightOperand;

    public Operator(String name, Type type, Node lefOperand, Node rightOperand) {
        this.name = name;
        this.type = type;
        this.lefOperand = lefOperand;
        this.rightOperand = rightOperand;
        setChildrenParent();
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public Node getLefOperand() {
        return lefOperand;
    }

    public Node getRightOperand() {
        return rightOperand;
    }

    @Override
    public List<Child> getChildren() {
        if (lefOperand == null)
            return Collections.singletonList(new Child(rightOperand, Role.Operand));
        return Arrays.asList(
                new Child(lefOperand, Role.Operand),
                new Child(rightOperand, Role.Operand));
    }

    @Override
    public boolean hasSameLocalVisualPattern(Node node) {
        return super.hasSameLocalVisualPattern(node) && ((Operator) node).name == name;
    }

    @Override
    public List<Object> getLocalBehavioralPatternValues() {
        List<Object> result = new ArrayList<>(super.getLocalBehavioralPatternValues());
        result.addAll(List.of(name, type));
        return result;
    }

    @Override
    public Summary getThisSummary() {
        return generateSummary(type, null);
    }

    @Override
    protected void toTextual(String linePrefix, List<Text> result) {
        if (lefOperand == null) {
            result.add(new Text(this, name + ' '));
        } else {
            lefOperand.toTextual(linePrefix + '\t', result);
            result.add(new Text(this, ' ' + name + ' '));
        }
        rightOperand.toTextual(linePrefix + '\t', result);
    }
}
