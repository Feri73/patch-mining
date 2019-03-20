package New.AST;

import java.util.List;

public class CompareOperator extends Operator{
    public CompareOperator(String name, Node lefOperand, Node rightOperand) {
        super(name, Type.Boolean, lefOperand, rightOperand);
    }

    public CompareOperator(String name, List<Node> operands) {
        super(name, Type.Boolean, operands);
    }
}
