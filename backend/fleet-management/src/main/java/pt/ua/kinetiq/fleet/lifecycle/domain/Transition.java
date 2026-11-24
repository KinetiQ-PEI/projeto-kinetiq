package pt.ua.kinetiq.fleet.lifecycle.domain;

import java.util.Objects;
import java.util.Set;

/** Uma linha da tabela de transições: de onde, com que evento, para onde e quem a pode pedir. */
public record Transition(BikeState from, LifecycleEvent event, BikeState to, Set<ActorRole> allowedRoles) {

    public Transition {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(to, "to");
        allowedRoles = Set.copyOf(allowedRoles);
        if (allowedRoles.isEmpty()) {
            throw new IllegalArgumentException("A transition needs at least one allowed role");
        }
    }

    public boolean isAllowedFor(ActorRole role) {
        return allowedRoles.contains(role);
    }
}
