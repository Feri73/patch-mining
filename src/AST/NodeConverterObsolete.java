package AST;

//import FSM.State;
//import FSM.StateMachine;
//import FSM.Transition;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.function.Consumer;

// include type caching for better type guessing (avoiding unnknwon types as much as possible)
public class NodeConverterObsolete {
//    StateMachine stateMachine = new StateMachine();
//
//    CtElement element;
//    Node.Child childRelation;
//
//    public NodeConverterObsolete() {
//
//    }
//
//    private void init() {
//        State element = new State("element", this::nothing);
//        State elementInvocation = getState("elementInvocation", this::elementInvocation, CtInvocation.class);
////        State elementMethodGetter = new State("elementMethodGetter")
//
//    }
//
//    private void nothing() {
//    }
//
//    private void elementInvocation(CtInvocation invocation) {
//        childRelation.node.value = invocation.getExecutable().getSimpleName();
//        childRelation.node.type = convertType(invocation.getExecutable().getType());
////        childRelation.node.label
////        if(invocation.)
//    }
//
//    private <T extends CtElement> State getState(String name, Consumer<T> handler, Class<T> elementType) {
//        return new State(name, () -> handler.accept(elementType.cast(element)));
//    }
//
//    // maybe include other things like stringbuilder, list etc.
//    private Node.Type convertType(CtTypeReference type) {
//        if (type == null)
//            return Node.Type.Unknown;
//        if (type instanceof CtArrayTypeReference) {
//            Node.Type res = Node.Type.Array;
//            res.argument = convertType(((CtArrayTypeReference) type).getComponentType());
//            return res;
//        } else {
//            type = type.box();
//            if ("java.lang".equals(type.getPackage().getSimpleName()))
//                switch (type.getSimpleName()) {
//                    case "Short":
//                    case "Long":
//                    case "Integer":
//                        return Node.Type.Integer;
//                    case "Float":
//                    case "Double":
//                        return Node.Type.Float;
//                    case "Boolean":
//                        return Node.Type.Boolean;
//                    case "String":
//                        return Node.Type.String;
//                    case "Void":
//                        return Node.Type.Void;
//                    default:
//                        throw new UnsupportedOperationException();
//                }
//            else if (type.getPackage().getSimpleName().startsWith("java"))
//                return Node.Type.JavaClass;
//            else
//                return Node.Type.OtherClass;
//        }
//    }
}
