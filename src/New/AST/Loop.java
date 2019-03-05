package New.AST;

import java.util.Arrays;
import java.util.List;

public class Loop extends Node {
    private Kind kind;

    private Node condition;
    private Block body;

    public Loop(Node condition, Block body, Kind kind) {
        this.condition = condition;
        this.body = body;
        this.kind = kind;
        setChildrenParent();
    }

    public Node getCondition() {
        return condition;
    }

    public Block getBody() {
        return body;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public List<Child> getChildren() {
        return Arrays.asList(
                new Child(condition, Node.Role.Condition),
                new Child(body, Node.Role.Body));
    }

    @Override
    public boolean hasSameLocalVisualPattern(Node node) {
        return super.hasSameLocalVisualPattern(node) && kind == ((Loop) node).kind;
    }

    public enum Kind {
        For,
        Foreach,
        While,
        DoWhile
    }
}
