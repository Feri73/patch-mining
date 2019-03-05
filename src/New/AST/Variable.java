package New.AST;

// do i need a Literal class as well?
public class Variable {
    private String name;
    private Node.Type type;
    private Kind kind;

    public Variable(String name, Node.Type type, Kind kind) {
        this.name = name;
        this.type = type;
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public Node.Type getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    public enum Kind {
        Local,
        Input
    }
}
