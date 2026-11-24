package pt.ua.kinetiq.fleet.lifecycle.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole.FLEET_OPERATOR;
import static pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole.MAINTENANCE_TECHNICIAN;
import static pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole.RIDER;
import static pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole.SYSTEM;
import static pt.ua.kinetiq.fleet.lifecycle.domain.BikeState.AVAILABLE;
import static pt.ua.kinetiq.fleet.lifecycle.domain.BikeState.IN_MAINTENANCE;
import static pt.ua.kinetiq.fleet.lifecycle.domain.BikeState.IN_USE;
import static pt.ua.kinetiq.fleet.lifecycle.domain.BikeState.RETIRED;
import static pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent.COMPLETE_REPAIR;
import static pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent.END_TRIP;
import static pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent.RETIRE;
import static pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent.SEND_TO_MAINTENANCE;
import static pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent.START_TRIP;

class BikeLifecycleStateMachineTest {

    private final BikeLifecycleStateMachine fsm = new BikeLifecycleStateMachine();

    /** A especificação, escrita à mão e independente do código: estado+evento → destino e papéis. */
    private static final Map<String, Expected> SPEC = Map.of(
            key(AVAILABLE, START_TRIP), new Expected(IN_USE, Set.of(RIDER, SYSTEM)),
            key(IN_USE, END_TRIP), new Expected(AVAILABLE, Set.of(RIDER, SYSTEM)),
            key(AVAILABLE, SEND_TO_MAINTENANCE), new Expected(IN_MAINTENANCE, Set.of(SYSTEM, FLEET_OPERATOR, MAINTENANCE_TECHNICIAN)),
            key(IN_USE, SEND_TO_MAINTENANCE), new Expected(IN_MAINTENANCE, Set.of(SYSTEM, FLEET_OPERATOR)),
            key(IN_MAINTENANCE, COMPLETE_REPAIR), new Expected(AVAILABLE, Set.of(MAINTENANCE_TECHNICIAN)),
            key(AVAILABLE, RETIRE), new Expected(RETIRED, Set.of(FLEET_OPERATOR)),
            key(IN_MAINTENANCE, RETIRE), new Expected(RETIRED, Set.of(FLEET_OPERATOR, MAINTENANCE_TECHNICIAN))
    );

    private record Expected(BikeState to, Set<ActorRole> roles) {
    }

    private static String key(BikeState s, LifecycleEvent e) {
        return s + "+" + e;
    }

    /** Todas as 4 × 5 × 5 = 100 combinações de estado, evento e papel. */
    static Stream<Arguments> allCombinations() {
        return Arrays.stream(BikeState.values()).flatMap(s ->
                Arrays.stream(LifecycleEvent.values()).flatMap(e ->
                        Arrays.stream(ActorRole.values()).map(r -> Arguments.of(s, e, r))));
    }

    @ParameterizedTest(name = "{0} + {1} by {2}")
    @MethodSource("allCombinations")
    void everyCombinationFollowsTheSpecification(BikeState from, LifecycleEvent event, ActorRole role) {
        Expected expected = SPEC.get(key(from, event));
        if (expected == null) {
            assertThatThrownBy(() -> fsm.next(from, event, role))
                    .isInstanceOf(InvalidTransitionException.class);
        } else if (!expected.roles().contains(role)) {
            assertThatThrownBy(() -> fsm.next(from, event, role))
                    .isInstanceOf(TransitionNotPermittedException.class);
        } else {
            assertThat(fsm.next(from, event, role)).isEqualTo(expected.to());
        }
    }

    @Test
    void tableHasExactlyTheSpecifiedTransitions() {
        Set<String> inCode = BikeLifecycleStateMachine.TRANSITIONS.stream()
                .map(t -> key(t.from(), t.event())).collect(Collectors.toSet());
        assertThat(inCode).isEqualTo(SPEC.keySet());
    }

    @ParameterizedTest
    @EnumSource(LifecycleEvent.class)
    void retiredIsFinal(LifecycleEvent event) {
        assertThat(fsm.allowedEvents(RETIRED)).isEmpty();
        assertThatThrownBy(() -> fsm.next(RETIRED, event, SYSTEM))
                .isInstanceOf(InvalidTransitionException.class)
                .hasMessageContaining("final state");
    }

    @Test
    void bikeInUseCannotBeRetiredDirectly() {
        assertThatThrownBy(() -> fsm.next(IN_USE, RETIRE, FLEET_OPERATOR))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void bikeInMaintenanceCannotStartATrip() {
        assertThatThrownBy(() -> fsm.next(IN_MAINTENANCE, START_TRIP, RIDER))
                .isInstanceOf(InvalidTransitionException.class)
                .satisfies(e -> assertThat(((InvalidTransitionException) e).getAllowedEvents())
                        .containsExactlyInAnyOrder(COMPLETE_REPAIR, RETIRE));
    }

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, RIDER, START_TRIP",
            "IN_USE, RIDER, END_TRIP",
            "IN_MAINTENANCE, MAINTENANCE_TECHNICIAN, COMPLETE_REPAIR;RETIRE",
            "AVAILABLE, FLEET_OPERATOR, SEND_TO_MAINTENANCE;RETIRE",
            "IN_USE, MAINTENANCE_TECHNICIAN, ''",
            "AVAILABLE, FLEET_ANALYST, ''",
    })
    void allowedEventsPerRole(BikeState state, ActorRole role, String events) {
        Set<LifecycleEvent> expected = events.isBlank() ? EnumSet.noneOf(LifecycleEvent.class)
                : Arrays.stream(events.split(";")).map(LifecycleEvent::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(LifecycleEvent.class)));
        assertThat(fsm.allowedEvents(state, role)).isEqualTo(expected);
    }

    @Test
    void everyStateIsReachableFromAvailable() {
        Set<BikeState> seen = EnumSet.of(AVAILABLE);
        Deque<BikeState> queue = new ArrayDeque<>(seen);
        while (!queue.isEmpty()) {
            BikeState s = queue.poll();
            BikeLifecycleStateMachine.TRANSITIONS.stream()
                    .filter(t -> t.from() == s && seen.add(t.to()))
                    .forEach(t -> queue.add(t.to()));
        }
        assertThat(seen).containsExactlyInAnyOrder(BikeState.values());
    }
}
