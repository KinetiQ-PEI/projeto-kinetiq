package pt.ua.kinetiq.fleet.lifecycle.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeState;

import java.time.Instant;

/**
 * Só os campos do ciclo de vida. Os dados de registo (número de série, IMEI, modelo, hub...)
 * são acrescentados à mesma tabela pelos endpoints de registo individual.
 */
@Entity
@Table(name = "bikes")
public class Bike {

    @Id
    @Column(name = "bike_id", length = 16, nullable = false, updatable = false)
    private String bikeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20, nullable = false)
    private BikeState state;

    @Column(name = "state_changed_at", nullable = false)
    private Instant stateChangedAt;

    /** Bloqueio otimista: dois pedidos ao mesmo tempo sobre a mesma bicicleta não se pisam. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Bike() {
        // JPA
    }

    /** Uma bicicleta nova entra sempre na frota como AVAILABLE. */
    public Bike(String bikeId, Instant registeredAt) {
        this.bikeId = bikeId;
        this.state = BikeState.AVAILABLE;
        this.stateChangedAt = registeredAt;
    }

    /**
     * Grava o novo estado. Não valida nada: quem decide se a transição é permitida é a
     * BikeLifecycleStateMachine, chamada pelo BikeLifecycleService (o único sítio que usa este método).
     */
    public void changeState(BikeState newState, Instant at) {
        this.state = newState;
        this.stateChangedAt = at;
    }

    public String getBikeId() {
        return bikeId;
    }

    public BikeState getState() {
        return state;
    }

    public Instant getStateChangedAt() {
        return stateChangedAt;
    }

    public long getVersion() {
        return version;
    }
}
