package pt.ua.kinetiq.fleet.lifecycle.domain;

import java.util.Set;

/** O evento não existe a partir do estado atual (ex.: START_TRIP numa bicicleta em manutenção). */
public class InvalidTransitionException extends RuntimeException {

    private final BikeState currentState;
    private final LifecycleEvent event;
    private final Set<LifecycleEvent> allowedEvents;

    public InvalidTransitionException(BikeState currentState, LifecycleEvent event, Set<LifecycleEvent> allowedEvents) {
        super(currentState.isTerminal()
                ? "Bike is " + currentState + ", which is a final state: no further transitions are possible"
                : "Cannot apply " + event + " to a bike that is " + currentState
                        + ". Allowed events from " + currentState + ": " + allowedEvents);
        this.currentState = currentState;
        this.event = event;
        this.allowedEvents = Set.copyOf(allowedEvents);
    }

    public BikeState getCurrentState() {
        return currentState;
    }

    public LifecycleEvent getEvent() {
        return event;
    }

    public Set<LifecycleEvent> getAllowedEvents() {
        return allowedEvents;
    }
}
