package New.AST;

import java.util.List;

public class Break extends Node {
    public Break() {
        setChildrenParent();
    }

    @Override
    public List<Child> getChildren() {
        return List.of();
    }

    @Override
    protected void toTextual(String linePrefix, List<Text> result) {
        result.add(new Text(this, "break"));
    }
}
