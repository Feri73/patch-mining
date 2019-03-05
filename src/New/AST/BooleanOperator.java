package New.AST;

public class BooleanOperator extends Operator{
    public BooleanOperator(String name, Node lefOperand, Node rightOperand) {
        super(name, Type.Boolean, lefOperand, rightOperand);
    }
}
