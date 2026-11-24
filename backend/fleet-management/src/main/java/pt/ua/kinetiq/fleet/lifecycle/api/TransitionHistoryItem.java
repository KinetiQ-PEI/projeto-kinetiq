package pt.ua.kinetiq.fleet.lifecycle.api;

import pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeState;
import pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent;
import pt.ua.kinetiq.fleet.lifecycle.persistence.BikeStateTransition;

import java.time.Instant;

public record TransitionHistoryItem(
        BikeState fromState,
        BikeState toState,
        LifecycleEvent event,
        ActorRole actorRole,
        String actorId,
        String reason,
        Instant occurredAt
) {
    static TransitionHistoryItem from(BikeStateTransition t) {
        return new TransitionHistoryItem(t.getFromState(), t.getToState(), t.getEvent(),
                t.getActorRole(), t.getActorId(), t.getReason(), t.getOccurredAt());
    }
}
