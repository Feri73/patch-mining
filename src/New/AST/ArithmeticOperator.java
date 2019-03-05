package New.AST;

public class ArithmeticOperator extends Operator{
    public ArithmeticOperator(String name, Type type, Node lefOperand, Node rightOperand) {
        super(name, type, lefOperand, rightOperand);
    }
}
