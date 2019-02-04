package FSM;

import java.util.function.BooleanSupplier;

public class Transition {
    private BooleanSupplier predicate;
    private State destination;

    public Transition(BooleanSupplier predicate, State destination) {
        this.predicate = predicate;
        this.destination = destination;
    }

    public State getDestination() {
        return destination;
    }

    public boolean decide(){
        return predicate.getAsBoolean();
    }
}
