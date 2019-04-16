package New.AST;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ArgumentsBlock extends Node {
    private List<Node> arguments;

    public ArgumentsBlock(List<Node> arguments) {
        this.arguments = List.copyOf(arguments);
        setChildrenParent();
    }

    public List<Node> getArguments() {
        return List.copyOf(arguments);
    }

    @Override
    public List<Child> getChildren() {
        return arguments.stream().map(x -> new Child(x, Role.MethodArgument)).collect(Collectors.toList());
    }

    @Override
    protected void toTextual(String linePrefix, List<Text> result) {
//        if (arguments.isEmpty())
//            result.add(new Text(this, "<empty>"));
        for (int i = 0; i < arguments.size(); i++) {
            Node argument = arguments.get(i);
            argument.toTextual(linePrefix + '\t', result);
            if (i < arguments.size() - 1)
                result.add(new Text(this, ", "));
        }
    }
}
