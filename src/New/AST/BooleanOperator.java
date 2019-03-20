package New.AST;

import java.util.List;

public class BooleanOperator extends Operator{
    public BooleanOperator(String name, Node lefOperand, Node rightOperand) {
        super(name, Type.Boolean, lefOperand, rightOperand);
    }

    public BooleanOperator(String name, List<Node> operands) {
        super(name, Type.Boolean, operands);
    }
}
