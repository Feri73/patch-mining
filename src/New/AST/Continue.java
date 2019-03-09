package New.AST;

import java.util.List;

public class Continue extends Node {
    public Continue() {
        setChildrenParent();
    }

    @Override
    public List<Child> getChildren() {
        return List.of();
    }

    @Override
    protected void toTextual(String linePrefix, List<Text> result) {
        result.add(new Text(this, "continue"));
    }
}
