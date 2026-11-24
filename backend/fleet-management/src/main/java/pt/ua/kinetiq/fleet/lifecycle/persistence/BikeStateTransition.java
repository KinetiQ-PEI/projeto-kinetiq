package pt.ua.kinetiq.fleet.lifecycle.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeState;
import pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent;

import java.time.Instant;
import java.util.UUID;

/** Uma linha do histórico de estados (rastreabilidade pedida no RF06/RF07). Nunca é alterada nem apagada. */
@Entity
@Table(name = "bike_state_transitions")
public class BikeStateTransition {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "bike_id", length = 16, nullable = false, updatable = false)
    private String bikeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 20, nullable = false, updatable = false)
    private BikeState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", length = 20, nullable = false, updatable = false)
    private BikeState toState;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", length = 32, nullable = false, updatable = false)
    private LifecycleEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 32, nullable = false, updatable = false)
    private ActorRole actorRole;

    @Column(name = "actor_id", length = 64, updatable = false)
    private String actorId;

    @Column(name = "reason", length = 500, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected BikeStateTransition() {
        // JPA
    }

    public BikeStateTransition(String bikeId, BikeState fromState, BikeState toState, LifecycleEvent event,
                               ActorRole actorRole, String actorId, String reason, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.bikeId = bikeId;
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
        this.actorRole = actorRole;
        this.actorId = actorId;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getBikeId() {
        return bikeId;
    }

    public BikeState getFromState() {
        return fromState;
    }

    public BikeState getToState() {
        return toState;
    }

    public LifecycleEvent getEvent() {
        return event;
    }

    public ActorRole getActorRole() {
        return actorRole;
    }

    public String getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
