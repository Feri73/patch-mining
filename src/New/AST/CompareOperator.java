package New.AST;

public class CompareOperator extends Operator{
    public CompareOperator(String name, Node lefOperand, Node rightOperand) {
        super(name, Type.Boolean, lefOperand, rightOperand);
    }
}
