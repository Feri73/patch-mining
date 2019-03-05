package New.AST;

import java.util.List;
import java.util.stream.Collectors;

public class Block extends Node {
    private List<Node> statements;

    public Block(List<Node> statements) {
        this.statements = List.copyOf(statements);
        setChildrenParent();
    }

    public List<Node> getStatements() {
        return List.copyOf(statements);
    }

    @Override
    public List<Child> getChildren() {
        return statements.stream().map(x -> new Child(x, Role.Statement)).collect(Collectors.toList());
    }
}
