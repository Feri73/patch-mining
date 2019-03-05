package New.AST;

import java.util.ArrayList;
import java.util.List;

public class Path extends ArrayList<Path.Element> {
    private static final long serialVersionUID = -6768538831401112971L;

    public Path() {
    }

    public Path(List<Element> content) {
        addAll(content);
    }

    public static class Element {
        public Node node;
        public Node.Role role;
        public Node.Summary adjunctSummary;

        public Element(Node node, Node.Role role, Node.Summary adjunctSummary) {
            this.node = node;
            this.role = role;
            this.adjunctSummary = adjunctSummary;
        }
    }
}
