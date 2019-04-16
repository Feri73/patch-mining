package New.AST;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.*;
import spoon.support.reflect.code.CtBlockImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

// include type caching for better type guessing (avoiding unnknwon types as much as possible)
// the ?: (and generally any CtExpresison that needs to become more than one root) should be converted to if in the preprocessing step (does it make it harder for the next stages? (applying the modifications on the code))
// the ++ (and generally expressions that are also assignment) should also be preprocessed-->so the asumption here is we do not have things like func(i=i+1) or func(i++)
// test this
// in all places where i get the first element of root[] returned from element(), i hsould check if the list is not empty (for(int i;....)
// i should have a map from Node to spoon nodes as well
public class SnippetConverter {
    private Map<Object, Variable> variablesMap;

    public SnippetConverter() {
        variablesMap = new HashMap<>();
    }

    // methodName is not a good approach, cuz many names with different signatures may exist
    public Snippet convertToSnippet(File file, String methodName) throws FileNotFoundException {
        Scanner scan = new Scanner(file);
        scan.useDelimiter("\\Z");
        String content = scan.next();
        scan.close();

        CtClass<?> clazz = Launcher.parseClass(content);
        return new Snippet(element(clazz.getMethodsByName(methodName).get(0).getBody()).get(0), getVariables());
    }

    // remove this method
    public Snippet convertToSnippet(CtBlock<?> methodBody) throws FileNotFoundException {
        return new Snippet(element(methodBody).get(0), getVariables());
    }

    private Variable getVariable(CtVariableReference<?> variableReferenceElement) {
        if (variablesMap.containsKey(variableReferenceElement))
            return variablesMap.get(variableReferenceElement);

        String name = variableReferenceElement.getSimpleName();
        Node.Type type = convertType(variableReferenceElement.getType());
        Variable.Kind kind;

        if (variableReferenceElement instanceof CtLocalVariableReference<?>
                || variableReferenceElement instanceof CtCatchVariableReference<?>)
            kind = Variable.Kind.Local;
        else if (variableReferenceElement instanceof CtParameterReference<?>
                || variableReferenceElement instanceof CtFieldReference<?>)
            kind = Variable.Kind.Input;
        else
            // unbound variable?
            throw new UnsupportedOperationException();

        variablesMap.put(variableReferenceElement, new Variable(name, type, kind));
        return variablesMap.get(variableReferenceElement);
    }

    private Variable getVariable(String name, Node.Type type, Variable.Kind kind) {
        if (variablesMap.containsKey(name))
            return variablesMap.get(name);

        variablesMap.put(name, new Variable(name, type, kind));
        return variablesMap.get(name);
    }

    public Set<Variable> getVariables() {
        return new HashSet<>(variablesMap.values());
    }

    private List<Node> element(CtElement element) {
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
        else if (element instanceof CtBreak)
            return Collections.singletonList(elementBreak((CtBreak) element));
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

            // these should be reconsidered
        else if (element instanceof CtVariableAccess<?>)
            return element(((CtVariableAccess<?>) element).getVariable());
        else if (element instanceof CtTry) {
            List<Node> result = new ArrayList<>();
            CtTry tryElement = (CtTry) element;
            result.add(element(tryElement.getBody()).get(0));
            for (CtCatch catcher : tryElement.getCatchers())
                result.add(element(catcher.getBody()).get(0));
            if (tryElement.getFinalizer() != null)
                result.add(element(tryElement.getFinalizer()).get(0));
            if (element instanceof CtTryWithResource)
                for (CtLocalVariable<?> localVariable : ((CtTryWithResource) element).getResources())
                    result.add(0, element(localVariable).get(0));
            return result;
        } else if (element instanceof CtReturn<?>) {
            Node assigner = element(((CtReturn<?>) element).getReturnedExpression()).get(0);
            Node.Type type;
            try {
                type = (Node.Type) assigner.getClass().getMethod("getType").invoke(assigner);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("return expression does not have type!");
            }
            Node assignee = new Value(getVariable("<ret>", type, Variable.Kind.Input));
            return Collections.singletonList(createAssignment(assigner, assignee));
        }

        return new ArrayList<>();
//        throw new UnsupportedOperationException(element.getClass().getSimpleName());
    }

    private Node elementInvocation(CtInvocation<?> invocationElement) {
        String name;
        MethodCall.Kind kind;
        Node.Type type = convertType(invocationElement.getExecutable().getType());

        List<Node> argumentsNodes = invocationElement.getArguments().stream()
                .map(x -> element(x).get(0)).collect(Collectors.toList());
        Node caller = null;

        if (invocationElement.getTarget() instanceof CtTypeAccess<?>) {
            // getType gives void here. DO something for it :|
            name = invocationElement.getTarget().toString() + '.' + invocationElement.getExecutable().getSimpleName();
            kind = MethodCall.Kind.ClassMethod;
        } else if (invocationElement.getTarget() instanceof CtExpression<?>) {
            name = invocationElement.getExecutable().getSimpleName();
            if (!(invocationElement.getTarget() instanceof CtThisAccess<?>))
                caller = element(invocationElement.getTarget()).get(0);
            if (name.startsWith("get") && Character.isUpperCase(name.charAt(3)))
                kind = MethodCall.Kind.ObjectMethod;
            else if (name.startsWith("set") && Character.isUpperCase(name.charAt(3)))
                kind = MethodCall.Kind.ObjectSetter;
            else
                kind = MethodCall.Kind.ObjectGetter;
        } else
            throw new UnsupportedOperationException();

        return createMethodCall(argumentsNodes, caller, name, kind, type);
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
        return new Value(getVariable(variableReferenceElement));
    }

    private Node elementBinaryOperator(CtBinaryOperator<?> binaryOperatorElement) {
        String name = getBinaryOperatorName(binaryOperatorElement.getKind());
        Node.Type type = convertType(binaryOperatorElement.getType());

        Node leftOperand = element(binaryOperatorElement.getLeftHandOperand()).get(0);
        Node rightOperand = element(binaryOperatorElement.getRightHandOperand()).get(0);

        switch (binaryOperatorElement.getKind()) {
            case OR:
            case AND:
                return new BooleanOperator(name, leftOperand, rightOperand);
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
                return new ArithmeticOperator(name, type, leftOperand, rightOperand);
            case EQ:
            case NE:
            case LT:
            case GT: // i do NOT convert < to > (and swap the operands) because it does not make differnece for the alanysis (i do not care about the exact operator)
            case LE:
            case GE:
            case INSTANCEOF:
                return new CompareOperator(name, leftOperand, rightOperand);
            default:
                throw new UnsupportedOperationException(); // support it :|
        }
    }

    private Node elementUnaryOperator(CtUnaryOperator<?> unaryOperatorElement) {
        String name = getUnaryOperatorName(unaryOperatorElement.getKind());
        Node.Type type = convertType(unaryOperatorElement.getType());

        Node operand = element(unaryOperatorElement.getOperand()).get(0);

        switch (unaryOperatorElement.getKind()) {
            case POS:
            case NEG:
            case COMPL:
                return new ArithmeticOperator(name, type, null, operand);
            case NOT:
                return new BooleanOperator(name, null, operand);
            case PREINC:
            case PREDEC:
            case POSTINC:
            case POSTDEC:
                throw new RuntimeException("unary increment and decrement are not supported.");
            default:
                throw new UnsupportedOperationException();
        }
    }

    private Node elementLiteral(CtLiteral<?> literalElement) {
        String name = literalElement.toString();
        Node.Type type = convertType(literalElement.getType());

        return new Value(type, name);
    }

    private Node elementArrayAccess(CtArrayAccess<?, ?> arrayAccessElement) {
        String value = "[]";
        Node.Type type = convertType(arrayAccessElement.getType());

        Node argument = element(arrayAccessElement.getIndexExpression()).get(0);
        Node caller = element(arrayAccessElement.getTarget()).get(0);

        return createMethodCall(Collections.singletonList(argument), caller, value,
                MethodCall.Kind.ObjectGetter, type);
    }

    private Node elementIf(CtIf ifElement) {
        Node condition = element(ifElement.getCondition()).get(0);
        Block thenBlock = (Block) element(ifElement.getThenStatement()).get(0);

        Block elseBlock = null;
        if (ifElement.getElseStatement() != null)
            elseBlock = (Block) element(ifElement.getElseStatement()).get(0);

        return createBranch(condition, thenBlock, elseBlock);
    }

    private List<Node> elementFor(CtFor forElement) {
        List<Node> nodes = new ArrayList<>();

        // assumption: the init statements all are converted to one root
        nodes.addAll(forElement.getForInit().stream().map(x -> element(x).get(0)).collect(Collectors.toList()));

        // do this for if and other similar stuff as well
        if (!(forElement.getBody() instanceof CtBlock<?>)) {
            CtBlock<?> block = new CtBlockImpl<>();
            block.addStatement(forElement.getBody());
            forElement.setBody(block);
        }

        // do not do this. instead add clone method in node and clone the created block statements
        for (CtStatement forUpdate : forElement.getForUpdate()) {
            forUpdate.delete();
            ((CtStatementList) forElement.getBody()).addStatement(forUpdate);
        }
        Block body = (Block) element(forElement.getBody()).get(0);

        Node condition;
        if (forElement.getExpression() == null)
            condition = new Value(Node.Type.Boolean, "true");
        else
            condition = element(forElement.getExpression()).get(0);

        nodes.add(createLoop(condition, body, Loop.Kind.For));

        return nodes;
    }

    private Node elementWhile(CtWhile whileElement) {
        Node condition = element(whileElement.getLoopingExpression()).get(0);
        Block body = (Block) element(whileElement.getBody()).get(0);

        return createLoop(condition, body, Loop.Kind.While);
    }

    private Node elementDo(CtDo doElement) {
        Node condition = element(doElement.getLoopingExpression()).get(0);
        Block body = (Block) element(doElement.getBody()).get(0);

        return createLoop(condition, body, Loop.Kind.DoWhile);
    }

    private Node elementBlock(CtBlock<?> blockElement) {
        List<Node> blockStatements = blockElement.getStatements().stream().
                flatMap(x -> element(x).stream()).collect(Collectors.toList());
        return new Block(blockStatements);
    }

    private Node elementLocalVariable(CtLocalVariable<?> localVariableELement) {
        if (localVariableELement.getAssignment() == null)
            return null;

        Node assignee = new Value(getVariable(localVariableELement.getReference()));
        Node assigner = element(localVariableELement.getAssignment()).get(0);

        return createAssignment(assigner, assignee);
    }

    private Node elementContinue(CtContinue continueElement) {
        return new Continue();
    }

    private Node elementBreak(CtBreak breakElement) {
        return new Break();
    }

    // note that spoon has problem in detecting fieldAccess of vars that are not defined (it spots them as typeAccess)
    private Node elementFieldAccess(CtFieldAccess<?> fieldAccessElement) {
        if (fieldAccessElement.getTarget() == null || fieldAccessElement.getTarget() instanceof CtThisAccess<?>)
            return new Value(getVariable(fieldAccessElement.getVariable()));

        String value = fieldAccessElement.getVariable().getSimpleName();
        Node.Type type = convertType(fieldAccessElement.getVariable().getType());

        Node caller = element(fieldAccessElement.getTarget()).get(0);

        return createMethodCall(new ArrayList<>(), caller, value, MethodCall.Kind.ObjectGetter, type);
    }

    private Node elementConstructorCall(CtConstructorCall<?> constructorCallElement) {
        String value;
        Node.Type type = convertType(constructorCallElement.getExecutable().getType());

        List<Node> argumentsNodes = constructorCallElement.getArguments().stream().map(x -> element(x).get(0)).collect(Collectors.toList());
        Node caller = null;

        value = constructorCallElement.getExecutable().getType().getSimpleName() + ".new";

        return createMethodCall(argumentsNodes, caller, value, MethodCall.Kind.ClassMethod, type);
    }

    private Node createAssignment(Node assigner, Node assignee) {
        return new Assignment(assigner, assignee);
    }

    private Node createLoop(Node condition, Block body, Loop.Kind kind) {
        return new Loop(condition, body, kind);
    }

    private Node createBranch(Node condition, Block thenBlock, Block elseBlock) {
        return new Branch(condition, thenBlock, elseBlock);
    }

    private Node createMethodCall(List<Node> argumentsNodes, Node caller, String methodName,
                                  MethodCall.Kind kind, Node.Type callType) {
        ArgumentsBlock argumentsBlock = new ArgumentsBlock(argumentsNodes);
        return new MethodCall(methodName, kind, callType, caller, argumentsBlock);
    }

    // maybe include other things like stringbuilder, list etc.
    private Node.Type convertType(CtTypeReference<?> type) {
        Node.Type res = null;
        if (type == null)
            res = Node.Type.Unknown;
        if (type instanceof CtArrayTypeReference<?>) {
            res = Node.Type.Array;
            res.argument = convertType(((CtArrayTypeReference<?>) type).getComponentType());
        } else {
            type = type.box();
            if (type.getPackage() != null && "java.lang".equals(type.getPackage().getSimpleName()))
                switch (type.getSimpleName()) {
                    case "Short":
                    case "Long":
                    case "Integer":
                        res = Node.Type.Integer;
                        break;
                    case "Float":
                    case "Double":
                        res = Node.Type.Float;
                        break;
                    case "Boolean":
                        res = Node.Type.Boolean;
                        break;
                    case "String":
                    case "Character":
                    case "Byte": // is it good to have byte here?
                        res = Node.Type.String;
                        break;
                    case "Void":
                        res = Node.Type.Void;
                        break;
                }
            if (res == null)
                if (type.getPackage() != null && type.getPackage().getSimpleName().startsWith("java"))
                    res = Node.Type.JavaClass;
                else
                    res = Node.Type.OtherClass;
        }
        if (type != null)
            res.name = type.toString();
        return res;
    }

    private String getBinaryOperatorName(BinaryOperatorKind operatorKind) {
        switch (operatorKind) {
            case OR:
                return "||";
            case AND:
                return "&&";
            case BITOR:
                return "|";
            case BITXOR:
                return "^";
            case BITAND:
                return "&";
            case EQ:
                return "==";
            case NE:
                return "!=";
            case LT:
                return "<";
            case GT:
                return ">";
            case LE:
                return "<=";
            case GE:
                return ">=";
            case SL:
                return "<<";
            case SR:
                return ">>";
            case USR:
                return ">>>";
            case PLUS:
                return "+";
            case MINUS:
                return "-";
            case MUL:
                return "*";
            case DIV:
                return "/";
            case MOD:
                return "%";
            case INSTANCEOF:
                return " instanceof ";
            default:
                throw new UnsupportedOperationException();
        }
    }

    private String getUnaryOperatorName(UnaryOperatorKind operatorKind) {
        switch (operatorKind) {
            case POS:
                return "+";
            case NEG:
                return "-";
            case NOT:
                return "!";
            case COMPL:
                return "~";
            case PREINC:
            case PREDEC:
            case POSTINC:
            case POSTDEC:
            default:
                throw new UnsupportedOperationException();
        }
    }

    // i should change my design i a way that i only use snippet in my interfaces, not node (because snippet can have many
    // good stuff in it like vars and varNodes
    public static class Snippet {
        private Node root;
        private Set<Variable> variables;

        public Snippet(Node root, Set<Variable> variables) {
            this.root = root;
            this.variables = variables;
        }

        public Node getRoot() {
            return root;
        }

        public Set<Variable> getVariables() {
            return variables;
        }
    }
}
