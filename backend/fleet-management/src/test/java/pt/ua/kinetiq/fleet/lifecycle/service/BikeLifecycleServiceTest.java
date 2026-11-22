package pt.ua.kinetiq.fleet.lifecycle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeLifecycleStateMachine;
import pt.ua.kinetiq.fleet.lifecycle.domain.InvalidTransitionException;
import pt.ua.kinetiq.fleet.lifecycle.domain.TransitionNotPermittedException;
import pt.ua.kinetiq.fleet.lifecycle.events.BikeStateChangedEvent;
import pt.ua.kinetiq.fleet.lifecycle.persistence.Bike;
import pt.ua.kinetiq.fleet.lifecycle.persistence.BikeRepository;
import pt.ua.kinetiq.fleet.lifecycle.persistence.BikeStateTransition;
import pt.ua.kinetiq.fleet.lifecycle.persistence.BikeStateTransitionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole.MAINTENANCE_TECHNICIAN;
import static pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole.RIDER;
import static pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole.SYSTEM;
import static pt.ua.kinetiq.fleet.lifecycle.domain.BikeState.AVAILABLE;
import static pt.ua.kinetiq.fleet.lifecycle.domain.BikeState.IN_MAINTENANCE;
import static pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent.COMPLETE_REPAIR;
import static pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent.SEND_TO_MAINTENANCE;
import static pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent.START_TRIP;

class BikeLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private static final String BIKE = "MB-2026-042";

    private BikeRepository bikes;
    private BikeStateTransitionRepository history;
    private ApplicationEventPublisher events;
    private BikeLifecycleService service;

    @BeforeEach
    void setUp() {
        bikes = mock(BikeRepository.class);
        history = mock(BikeStateTransitionRepository.class);
        events = mock(ApplicationEventPublisher.class);
        service = new BikeLifecycleService(bikes, history, new BikeLifecycleStateMachine(), events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validTransitionUpdatesStateWritesHistoryAndPublishesEvent() {
        Bike bike = new Bike(BIKE, NOW.minusSeconds(3600));
        when(bikes.findById(BIKE)).thenReturn(Optional.of(bike));

        Bike result = service.applyEvent(BIKE, SEND_TO_MAINTENANCE, SYSTEM, "ml-module", "Battery draining 3x faster");

        assertThat(result.getState()).isEqualTo(IN_MAINTENANCE);
        assertThat(result.getStateChangedAt()).isEqualTo(NOW);
        verify(bikes).save(bike);

        ArgumentCaptor<BikeStateTransition> row = ArgumentCaptor.forClass(BikeStateTransition.class);
        verify(history).save(row.capture());
        assertThat(row.getValue().getFromState()).isEqualTo(AVAILABLE);
        assertThat(row.getValue().getToState()).isEqualTo(IN_MAINTENANCE);
        assertThat(row.getValue().getActorId()).isEqualTo("ml-module");
        assertThat(row.getValue().getOccurredAt()).isEqualTo(NOW);

        ArgumentCaptor<BikeStateChangedEvent> sent = ArgumentCaptor.forClass(BikeStateChangedEvent.class);
        verify(events).publishEvent(sent.capture());
        assertThat(sent.getValue().bikeId()).isEqualTo(BIKE);
        assertThat(sent.getValue().previousState()).isEqualTo(AVAILABLE);
        assertThat(sent.getValue().newState()).isEqualTo(IN_MAINTENANCE);
        assertThat(sent.getValue().timestamp()).isEqualTo(NOW.toEpochMilli());
        assertThat(sent.getValue().schemaVersion()).isEqualTo("1.0");
    }

    @Test
    void invalidTransitionChangesNothing() {
        Bike bike = new Bike(BIKE, NOW);
        when(bikes.findById(BIKE)).thenReturn(Optional.of(bike));

        assertThatThrownBy(() -> service.applyEvent(BIKE, COMPLETE_REPAIR, MAINTENANCE_TECHNICIAN, "t1", null))
                .isInstanceOf(InvalidTransitionException.class);

        assertThat(bike.getState()).isEqualTo(AVAILABLE);
        verify(bikes, never()).save(any());
        verify(history, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void roleWithoutPermissionChangesNothing() {
        Bike bike = new Bike(BIKE, NOW);
        when(bikes.findById(BIKE)).thenReturn(Optional.of(bike));

        assertThatThrownBy(() -> service.applyEvent(BIKE, SEND_TO_MAINTENANCE, RIDER, "r1", null))
                .isInstanceOf(TransitionNotPermittedException.class);

        assertThat(bike.getState()).isEqualTo(AVAILABLE);
        verify(history, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void unknownBikeIsReported() {
        when(bikes.findById("MB-2026-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyEvent("MB-2026-999", START_TRIP, RIDER, "r1", null))
                .isInstanceOf(BikeNotFoundException.class);
    }

    @Test
    void historyOfUnknownBikeIsReported() {
        when(bikes.existsById("MB-2026-999")).thenReturn(false);

        assertThatThrownBy(() -> service.getHistory("MB-2026-999"))
                .isInstanceOf(BikeNotFoundException.class);
    }
}
