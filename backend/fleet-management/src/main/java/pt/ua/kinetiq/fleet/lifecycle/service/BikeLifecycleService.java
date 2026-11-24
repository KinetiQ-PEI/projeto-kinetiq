package pt.ua.kinetiq.fleet.lifecycle.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeLifecycleStateMachine;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeState;
import pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent;
import pt.ua.kinetiq.fleet.lifecycle.events.BikeStateChangedEvent;
import pt.ua.kinetiq.fleet.lifecycle.persistence.Bike;
import pt.ua.kinetiq.fleet.lifecycle.persistence.BikeRepository;
import pt.ua.kinetiq.fleet.lifecycle.persistence.BikeStateTransition;
import pt.ua.kinetiq.fleet.lifecycle.persistence.BikeStateTransitionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Único ponto de entrada para mudar o estado de uma bicicleta.
 * Faz, numa só transação: valida com a máquina de estados, grava o novo estado,
 * grava a linha de histórico e prepara o evento para o Kafka.
 */
@Service
public class BikeLifecycleService {

    private final BikeRepository bikes;
    private final BikeStateTransitionRepository history;
    private final BikeLifecycleStateMachine stateMachine;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BikeLifecycleService(BikeRepository bikes, BikeStateTransitionRepository history,
                                BikeLifecycleStateMachine stateMachine, ApplicationEventPublisher events,
                                Clock clock) {
        this.bikes = bikes;
        this.history = history;
        this.stateMachine = stateMachine;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Bike applyEvent(String bikeId, LifecycleEvent event, ActorRole role, String actorId, String reason) {
        Bike bike = bikes.findById(bikeId).orElseThrow(() -> new BikeNotFoundException(bikeId));
        BikeState from = bike.getState();
        BikeState to = stateMachine.next(from, event, role); // lança exceção se não for permitido
        Instant now = clock.instant();

        bike.changeState(to, now);
        bikes.save(bike);
        history.save(new BikeStateTransition(bikeId, from, to, event, role, actorId, reason, now));

        events.publishEvent(new BikeStateChangedEvent(
                BikeStateChangedEvent.SCHEMA_VERSION, UUID.randomUUID().toString(), bikeId,
                now.toEpochMilli(), from, to, event, role, actorId, reason));
        return bike;
    }

    @Transactional(readOnly = true)
    public Bike getBike(String bikeId) {
        return bikes.findById(bikeId).orElseThrow(() -> new BikeNotFoundException(bikeId));
    }

    @Transactional(readOnly = true)
    public List<BikeStateTransition> getHistory(String bikeId) {
        if (!bikes.existsById(bikeId)) {
            throw new BikeNotFoundException(bikeId);
        }
        return history.findByBikeIdOrderByOccurredAtDesc(bikeId);
    }

    public Set<LifecycleEvent> allowedEvents(BikeState state, ActorRole role) {
        return role == null ? stateMachine.allowedEvents(state) : stateMachine.allowedEvents(state, role);
    }
}
