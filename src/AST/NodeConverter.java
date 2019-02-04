package AST;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.*;

import java.util.*;
import java.util.stream.Collectors;

// include type caching for better type guessing (avoiding unnknwon types as much as possible)
// the ?: (and generally any CtExpresison that needs to become more than one node) should be converted to if in the preprocessing step (does it make it harder for the next stages? (applying the modifications on the code))
// the ++ (and generally expressions that are also assignment) should also be preprocessed-->so the asumption here is we do not have things like func(i=i+1) or func(i++)
// test this
// in all places where i get the first element of node[] returned from element(), i hsould check if the list is not empty (for(int i;....)
public class NodeConverter {

    public List<Node> element(CtElement element) {
        if (element instanceof CtInvocation<?>)
            return Collections.singletonList(elementInvocation((CtInvocation<?>) element));
        else if (element instanceof CtAssignment<?, ?>)
            if (element instanceof CtOperatorAssignment<?, ?>)
                return null; // complete it
            else
                return Collections.singletonList(elementAssignment((CtAssignment<?, ?>) element));
        else if (element instanceof CtVariableReference<?> && !(element instanceof CtFieldReference<?>))
            return Collections.singletonList(elementVariableReference((CtVariableReference<?>) element));
        else if (element instanceof CtBinaryOperator<?>)
            return Collections.singletonList(elementBinaryOperator((CtBinaryOperator<?>) element));
        else if (element instanceof CtUnaryOperator<?>)
            return Collections.singletonList(elementUnaryOperator((CtUnaryOperator<?>) element));
        else if (element instanceof CtLiteral<?>)
            return Collections.singletonList(elementLiteral((CtLiteral<?>) element));
        else if (element instanceof CtArrayAccess<?, ?>)
            return Collections.singletonList(elementArrayAccess((CtArrayAccess<?, ?>) element));
        else if (element instanceof CtIf)
            return Collections.singletonList(elementIf((CtIf) element));
        else if (element instanceof CtFor)
            return elementFor((CtFor) element);
        else if (element instanceof CtWhile)
            return Collections.singletonList(elementWhile((CtWhile) element));
        else if (element instanceof CtDo)
            return Collections.singletonList(elementDo((CtDo) element));
        else if (element instanceof CtBlock<?>)
            return Collections.singletonList(elementBlock((CtBlock<?>) element));
        else if (element instanceof CtContinue)
            return Collections.singletonList(elementContinue((CtContinue) element));
        else if (element instanceof CtLocalVariable<?>) {
            Node localVariable = elementLocalVariable((CtLocalVariable<?>) element);
            if (localVariable == null)
                return new ArrayList<>();
            else
                return Collections.singletonList(localVariable);
        } else if (element instanceof CtFieldAccess<?>)
            return Collections.singletonList(elementFieldAccess((CtFieldAccess<?>) element));
        else if (element instanceof CtConstructorCall<?>)
            return Collections.singletonList(elementConstructorCall((CtConstructorCall<?>) element));

        else if (element instanceof CtVariableAccess<?>)
            return element(((CtVariableAccess<?>) element).getVariable());

        return new ArrayList<>();
//        throw new UnsupportedOperationException(element.getClass().getSimpleName());
    }

    private Node elementInvocation(CtInvocation<?> invocationElement) {
        String value;
        Node.NodeLabel label;
        Node.Type type = convertType(invocationElement.getExecutable().getType());

        List<Node> argumentsNodes = invocationElement.getArguments().stream().map(x -> element(x).get(0)).collect(Collectors.toList());
        Node caller = null;

        if (invocationElement.getTarget() instanceof CtTypeAccess<?>) {
            value = invocationElement.getTarget().getType().getSimpleName() + '.' + invocationElement.getExecutable().getSimpleName();
            label = Node.NodeLabel.ClassMethodCall;
        } else if (invocationElement.getTarget() instanceof CtExpression<?>) {
            value = invocationElement.getExecutable().getSimpleName();
            if (!(invocationElement.getTarget() instanceof CtThisAccess<?>))
                caller = element(invocationElement.getTarget()).get(0);

            if (value.startsWith("get") && Character.isUpperCase(value.charAt(3)))
                label = Node.NodeLabel.ObjectGetterCall;
            else if (value.startsWith("set") && Character.isUpperCase(value.charAt(3)))
                label = Node.NodeLabel.ObjectSetterCall;
            else
                label = Node.NodeLabel.ObjectMethodCall;
        } else
            throw new UnsupportedOperationException();

        return createMethodCall(argumentsNodes, caller, value, label, type);
    }

    // create one of these create** methods for this as well so that we can reuse code for CtOperatorAssignment elements
    private Node elementAssignment(CtAssignment<?, ?> assignmentElement) {
        if (assignmentElement instanceof CtOperatorAssignment)
            throw new RuntimeException("elementAssignment does not accept CtOperatorAssignment.");

        Node assigner = element(assignmentElement.getAssignment()).get(0);
        Node assignee = element(assignmentElement.getAssigned()).get(0);

        return createAssignment(assigner, assignee);
    }

    private Node elementVariableReference(CtVariableReference<?> variableReferenceElement) {
        if (variableReferenceElement instanceof CtFieldReference<?>)
            throw new RuntimeException("elementVariableReference does not accept CtFieldReference.");

        String value = variableReferenceElement.getSimpleName();
        Node.NodeLabel label = Node.NodeLabel.Value;
        Node.Type type = convertType(variableReferenceElement.getType());
        EnumSet<Node.ValueSource> sources;

        if (variableReferenceElement instanceof CtLocalVariableReference<?>
                || variableReferenceElement instanceof CtCatchVariableReference<?>)
            sources = EnumSet.of(Node.ValueSource.Variable);
        else if (variableReferenceElement instanceof CtParameterReference<?>)
            sources = EnumSet.of(Node.ValueSource.Input);
        else
            // unbound variable?
            throw new UnsupportedOperationException();

        return new Node(label, value, sources, type);
    }

    private Node elementBinaryOperator(CtBinaryOperator<?> binaryOperatorElement) {
        String value = binaryOperatorElement.getKind().name(); // so, we don't have +,-,... but their names. no differnece tho
        Node.NodeLabel label;
        Node.Type type = convertType(binaryOperatorElement.getType());
        EnumSet<Node.ValueSource> sources = EnumSet.noneOf(Node.ValueSource.class);

        switch (binaryOperatorElement.getKind()) {
            case OR:
            case AND:
                label = Node.NodeLabel.BooleanOperator;
                break;
            case BITOR:
            case BITXOR:
            case BITAND:
            case PLUS:
            case MINUS:
            case MUL:
            case DIV:
            case MOD:
            case SL:
            case SR:
            case USR:
                label = Node.NodeLabel.ArithmeticOperator;
                break;
            case EQ:
            case NE:
            case LT:
            case GT: // i do NOT convert < to > (and swap the operands) because it does not make differnece for the alanysis (i do not care about the exact operator)
            case LE:
            case GE:
                label = Node.NodeLabel.CompareOperator;
                break;
            case INSTANCEOF:
            default:
                throw new UnsupportedOperationException(); // support it :|
        }

        Node operand1 = element(binaryOperatorElement.getLeftHandOperand()).get(0);
        Node operand2 = element(binaryOperatorElement.getRightHandOperand()).get(0);

        sources.addAll(operand1.sources);
        sources.addAll(operand2.sources);

        Node operator = new Node(label, value, sources, type);
        operator.addChild(operand1, Node.EdgeLabel.Operand);
        operator.addChild(operand2, Node.EdgeLabel.Operand);

        return operator;
    }

    private Node elementUnaryOperator(CtUnaryOperator<?> unaryOperatorElement) {
        String value = unaryOperatorElement.getKind().name(); // so, we don't have +,-,... but their names. no differnece tho
        Node.NodeLabel label;
        Node.Type type = convertType(unaryOperatorElement.getType());
        EnumSet<Node.ValueSource> sources = EnumSet.noneOf(Node.ValueSource.class);

        switch (unaryOperatorElement.getKind()) {
            case POS:
            case NEG:
            case COMPL:
                label = Node.NodeLabel.ArithmeticOperator;
                break;
            case NOT:
                label = Node.NodeLabel.BooleanOperator;
                break;
            case PREINC:
            case PREDEC:
            case POSTINC:
            case POSTDEC:
                throw new RuntimeException("unary increment and decrement are not supported.");
            default:
                throw new UnsupportedOperationException();
        }

        Node operand1 = element(unaryOperatorElement.getOperand()).get(0);

        sources.addAll(operand1.sources);

        Node operator = new Node(label, value, sources, type);
        operator.addChild(operand1, Node.EdgeLabel.Operand);

        return operator;
    }

    private Node elementLiteral(CtLiteral<?> literalElement) {
        String value = literalElement.toString();
        Node.NodeLabel label = Node.NodeLabel.Value;
        Node.Type type = convertType(literalElement.getType());
        EnumSet<Node.ValueSource> sources = EnumSet.of(Node.ValueSource.Literal);

        return new Node(label, value, sources, type);
    }

    private Node elementArrayAccess(CtArrayAccess<?, ?> arrayAccessElement) {
        String value = "[]";
        Node.NodeLabel label = Node.NodeLabel.ObjectGetterCall;
        Node.Type type = convertType(arrayAccessElement.getType());

        Node argument = element(arrayAccessElement.getIndexExpression()).get(0);
        Node caller = element(arrayAccessElement.getTarget()).get(0);

        return createMethodCall(Collections.singletonList(argument), caller, value, label, type);
    }

    private Node elementIf(CtIf ifElement) {
        String value = "if";

        Node condition = element(ifElement.getCondition()).get(0);
        Node thenBlock = element(ifElement.getThenStatement()).get(0);

        Node elseBlock = null;
        if (ifElement.getElseStatement() != null)
            elseBlock = element(ifElement.getElseStatement()).get(0);

        return createBranch(condition, thenBlock, elseBlock, value);
    }

    private List<Node> elementFor(CtFor forElement) {
        List<Node> nodes = new ArrayList<>();

        // assumption: the init statements all are converted to one node
        nodes.addAll(forElement.getForInit().stream().map(x -> element(x).get(0)).collect(Collectors.toList()));

        Node block = element(forElement.getBody()).get(0);
        forElement.getForUpdate().forEach(x -> block.addChild(element(x).get(0), Node.EdgeLabel.Statement));

        Node condition;
        if (forElement.getExpression() == null)
            condition = new Node(Node.NodeLabel.Value, "true", EnumSet.of(Node.ValueSource.Literal), Node.Type.Boolean);
        else
            condition = element(forElement.getExpression()).get(0);

        nodes.add(createLoop(condition, block, "for"));

        return nodes;
    }

    private Node elementWhile(CtWhile whileElement) {
        Node condition = element(whileElement.getLoopingExpression()).get(0);
        Node block = element(whileElement.getBody()).get(0);

        return createLoop(condition, block, "while");
    }

    private Node elementDo(CtDo doElement) {
        Node condition = element(doElement.getLoopingExpression()).get(0);
        Node block = element(doElement.getBody()).get(0);

        return createLoop(condition, block, "do");
    }

    private Node elementBlock(CtBlock<?> blockElement) {
        Node block = new Node(Node.NodeLabel.Block, null);
        blockElement.getStatements().forEach(x -> element(x).forEach(y -> block.addChild(y, Node.EdgeLabel.Statement)));
        return block;
    }

    private Node elementLocalVariable(CtLocalVariable<?> localVariableELement) {
        if (localVariableELement.getAssignment() == null)
            return null;

        Node assignee = new Node(Node.NodeLabel.Value, localVariableELement.getSimpleName(),
                EnumSet.of(Node.ValueSource.Variable), convertType(localVariableELement.getType()));
        Node assigner = element(localVariableELement.getAssignment()).get(0);

        return createAssignment(assigner, assignee);
    }

    private Node elementContinue(CtContinue continueElement) {
        return new Node(Node.NodeLabel.Continue, null);
    }

    // note that spoon has problem in detecting fieldAccess of vars that are not defined (it spots them as typeAccess)
    private Node elementFieldAccess(CtFieldAccess<?> fieldAccessElement) {
        if (fieldAccessElement.getTarget() == null || fieldAccessElement.getTarget() instanceof CtThisAccess<?>)
            return new Node(Node.NodeLabel.Value, fieldAccessElement.getVariable().getSimpleName(),
                    EnumSet.of(Node.ValueSource.Input), convertType(fieldAccessElement.getVariable().getType()));

        String value = fieldAccessElement.getVariable().getSimpleName();
        Node.NodeLabel label = Node.NodeLabel.ObjectGetterCall;
        Node.Type type = convertType(fieldAccessElement.getVariable().getType());

        Node caller = element(fieldAccessElement.getTarget()).get(0);

        return createMethodCall(new ArrayList<>(), caller, value, label, type);
    }

    private Node elementConstructorCall(CtConstructorCall<?> constructorCallElement) {
        String value;
        Node.NodeLabel label;
        Node.Type type = convertType(constructorCallElement.getExecutable().getType());

        List<Node> argumentsNodes = constructorCallElement.getArguments().stream().map(x -> element(x).get(0)).collect(Collectors.toList());
        Node caller = null;

        value = constructorCallElement.getExecutable().getType().getSimpleName() + ".new";
        label = Node.NodeLabel.ClassMethodCall;

        return createMethodCall(argumentsNodes, caller, value, label, type);
    }

    private Node createAssignment(Node assigner, Node assignee) {
        String value = "=";
        Node.NodeLabel label = Node.NodeLabel.Assignment;
        Node.Type type = Node.Type.Void; // really?
        EnumSet<Node.ValueSource> sources = EnumSet.noneOf(Node.ValueSource.class);

        sources.addAll(assigner.sources);
        sources.addAll(assignee.sources);

        Node assignment = new Node(label, value, sources, type);
        assignment.addChild(assigner, Node.EdgeLabel.Assigner);
        assignment.addChild(assignee, Node.EdgeLabel.Assignee);

        return assignment;
    }

    private Node createLoop(Node condition, Node block, String loopValue) {
        Node loop = new Node(Node.NodeLabel.Loop, loopValue);
        loop.addChild(condition, Node.EdgeLabel.Condition);
        loop.addChild(block, Node.EdgeLabel.Block);
        return loop;
    }

    private Node createBranch(Node condition, Node thenBlock, Node elseBlock, String branchValue) {
        Node branch = new Node(Node.NodeLabel.Branch, branchValue);
        branch.addChild(condition, Node.EdgeLabel.Condition);
        branch.addChild(thenBlock, Node.EdgeLabel.Block);
        if (elseBlock != null)
            branch.addChild(elseBlock, Node.EdgeLabel.Block);
        return branch;
    }

    private Node createMethodCall(List<Node> argumentsNodes, Node caller, String callValue, Node.NodeLabel callLabel, Node.Type callType) {
        EnumSet<Node.ValueSource> callSources = EnumSet.noneOf(Node.ValueSource.class);

        EnumSet<Node.ValueSource> argumentsSources = EnumSet.noneOf(Node.ValueSource.class);
        for (Node argument : argumentsNodes)
            argumentsSources.addAll(argument.sources);

        Node arguments = new Node(Node.NodeLabel.Arguments, null, argumentsSources, null);
        argumentsNodes.forEach(x -> arguments.addChild(x, Node.EdgeLabel.MethodArgument));

        callSources.addAll(arguments.sources);
        if (caller != null)
            callSources.addAll(caller.sources);

        Node methodCall = new Node(callLabel, callValue, callSources, callType);
        if (caller != null)
            methodCall.addChild(caller, Node.EdgeLabel.MethodCaller);
        methodCall.addChild(arguments, Node.EdgeLabel.MethodArguments);

        return methodCall;
    }

    // maybe include other things like stringbuilder, list etc.
    private Node.Type convertType(CtTypeReference<?> type) {
        if (type == null)
            return Node.Type.Unknown;
        if (type instanceof CtArrayTypeReference<?>) {
            Node.Type res = Node.Type.Array;
            res.argument = convertType(((CtArrayTypeReference<?>) type).getComponentType());
            return res;
        } else {
            type = type.box();
            if (type.getPackage() != null && "java.lang".equals(type.getPackage().getSimpleName()))
                switch (type.getSimpleName()) {
                    case "Short":
                    case "Long":
                    case "Integer":
                        return Node.Type.Integer;
                    case "Float":
                    case "Double":
                        return Node.Type.Float;
                    case "Boolean":
                        return Node.Type.Boolean;
                    case "String":
                    case "Character":
                    case "Byte": // is it good to have byte here?
                        return Node.Type.String;
                    case "Void":
                        return Node.Type.Void;
                }
            if (type.getPackage() != null && type.getPackage().getSimpleName().startsWith("java"))
                return Node.Type.JavaClass;
            else
                return Node.Type.OtherClass;
        }
    }
}
