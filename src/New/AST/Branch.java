package New.AST;

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
        List<Child> result = Arrays.asList(
                new Child(condition, Role.Condition),
                new Child(thenBody, Role.Body));
        if (elseBody != null)
            result.add(new Child(elseBody, Role.Body));
        return result;
    }
}