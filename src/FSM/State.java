package FSM;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class State {
    private String name;
    private List<Transition> transitions;
    private Runnable handler;
    private boolean isFinal;

    public State(String name, Runnable handler, boolean isFinal) {
        this.name = name;
        this.handler = handler;
        this.isFinal = isFinal;
        transitions = new ArrayList<Transition>();
    }

    public State(String name, Runnable handler) {
        this(name, handler, false);
    }

    public String getName() {
        return name;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public List<Transition> getTransitions() {
        return new ArrayList<>(transitions);
    }

    public void addTransition(BooleanSupplier predicate, State destination) {
        addTransition(new Transition(predicate, destination));
    }

    public void addTransition(Transition transition) {
        transitions.add(transition);
    }

    public void run() {
        handler.run();
    }
}
