package FSM;

import java.util.ArrayList;
import java.util.List;

public class StateMachine {
    private State initState;
    private List<State> states;

    public StateMachine() {
        states = new ArrayList<State>();
    }

    public void setInitState(State initState) {
        this.initState = initState;
    }

    public void addState(State state) {
        states.add(state);
    }

    public void run() throws Exception {
        if (initState == null)
            throw new Exception("initial state not identified");
        State currentState = initState;

        while (true) {
            currentState.run();
            for (Transition transition : currentState.getTransitions())
                if (transition.decide()) {
                    if (currentState != transition.getDestination())
                        currentState = transition.getDestination();
                    if (currentState.isFinal())
                        return;
                }
        }
    }
}
