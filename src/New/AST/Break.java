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
}
