package New.AST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Branch extends Node {
    private Node condition;
    private Block thenBody;
    private Block elseBody;

    public Branch(Node condition, Block thenBody, Block elseBody) {
        this.condition = condition;
        this.thenBody = thenBody;
        this.elseBody = elseBody;
        setChildrenParent();
    }

    public Node getCondition() {
        return condition;
    }

    public Block getThenBody() {
        return thenBody;
    }

    public Block getElseBody() {
        return elseBody;
    }

    @Override
    public List<Child> getChildren() {
        List<Child> result = new ArrayList<>();
        result.add(new Child(condition, Role.Condition));
        result.add(new Child(thenBody, Role.Body));
        if (elseBody != null)
            result.add(new Child(elseBody, Role.Body));
        return result;
    }

    @Override
    protected void toTextual(String linePrefix, List<Text> result) {
        result.add(new Text(this, "if ("));
        condition.toTextual(linePrefix + '\t', result);
        result.add(new Text(this, ") "));
        thenBody.toTextual(linePrefix + '\t', result);
        if (elseBody != null) {
            result.add(new Text(this, " else "));
            elseBody.toTextual(linePrefix + '\t', result);
        }
    }
}