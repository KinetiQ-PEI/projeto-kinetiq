package pt.ua.kinetiq.fleet.lifecycle.domain;

/** A transição existe, mas este papel não a pode pedir (ex.: um rider a retirar uma bicicleta). */
public class TransitionNotPermittedException extends RuntimeException {

    private final BikeState currentState;
    private final LifecycleEvent event;
    private final ActorRole role;

    public TransitionNotPermittedException(BikeState currentState, LifecycleEvent event, ActorRole role) {
        super("Role " + role + " is not allowed to apply " + event + " to a bike that is " + currentState);
        this.currentState = currentState;
        this.event = event;
        this.role = role;
    }

    public BikeState getCurrentState() {
        return currentState;
    }

    public LifecycleEvent getEvent() {
        return event;
    }

    public ActorRole getRole() {
        return role;
    }
}
