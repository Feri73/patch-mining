package New.AST;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Block extends Node {
    private List<Node> statements;

    public Block(List<Node> statements) {
        this.statements = new ArrayList<>(statements);
        setChildrenParent();
    }

    public List<Node> getStatements() {
        return new ArrayList<>(statements);
    }

    @Override
    public List<Child> getChildren() {
        return statements.stream().map(x -> new Child(x, Role.Statement)).collect(Collectors.toList());
    }

    @Override
    protected void toTextual(String linePrefix, List<Text> result) {
        result.add(new Text(this, "{\n"));
        for (Node statement : statements) {
            result.add(new Text(this, linePrefix + '\t'));
            statement.toTextual(linePrefix, result);
            result.add(new Text(this, ";\n"));
        }
        result.add(new Text(this, linePrefix + '}'));
    }
}
