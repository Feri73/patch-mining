package New.AST;

import java.util.Arrays;
import java.util.List;

public class Assignment extends Node {
    private Node assigner;
    private Node assignee;

    public Assignment(Node assigner, Node assignee) {
        this.assigner = assigner;
        this.assignee = assignee;
        setChildrenParent();
    }

    public Node getAssigner() {
        return assigner;
    }

    public Node getAssignee() {
        return assignee;
    }

    @Override
    public List<Child> getChildren() {
        return Arrays.asList(
                new Child(assigner, Role.Assigner),
                new Child(assignee, Role.Assignee));
    }

    @Override
    protected void toTextual(String linePrefix, List<Text> result) {
        assignee.toTextual(linePrefix + '\t', result);
        result.add(new Text(this, " = "));
        assigner.toTextual(linePrefix + '\t', result);
    }
}
