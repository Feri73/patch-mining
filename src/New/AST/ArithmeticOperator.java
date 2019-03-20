package New.AST;

import java.util.List;

public class ArithmeticOperator extends Operator{
    public ArithmeticOperator(String name, Type type, Node lefOperand, Node rightOperand) {
        super(name, type, lefOperand, rightOperand);
    }

    public ArithmeticOperator(String name, Type type, List<Node> operands) {
        super(name, type, operands);
    }
}
