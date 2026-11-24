package pt.ua.kinetiq.fleet.lifecycle.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

/**
 * Máquina de estados finitos do ciclo de vida da bicicleta (RF06).
 *
 * <p>Tudo o que não está na tabela {@link #TRANSITIONS} é proibido. Não depende do Spring
 * nem da base de dados: é só a regra, por isso testa-se sem arrancar nada.
 *
 * <p>Transições (o diagrama está no README do serviço):
 * <ul>
 *   <li>AVAILABLE + START_TRIP → IN_USE</li>
 *   <li>IN_USE + END_TRIP → AVAILABLE</li>
 *   <li>AVAILABLE ou IN_USE + SEND_TO_MAINTENANCE → IN_MAINTENANCE</li>
 *   <li>IN_MAINTENANCE + COMPLETE_REPAIR → AVAILABLE</li>
 *   <li>AVAILABLE ou IN_MAINTENANCE + RETIRE → RETIRED (final)</li>
 * </ul>
 * Uma bicicleta em uso não pode ser retirada diretamente: primeiro termina a viagem
 * ou vai para manutenção.
 */
public final class BikeLifecycleStateMachine {

    /** A tabela de transições. Para mudar o comportamento, muda-se só esta lista (e os testes). */
    public static final List<Transition> TRANSITIONS = List.of(
            new Transition(AVAILABLE, START_TRIP, IN_USE, Set.of(RIDER, SYSTEM)),
            new Transition(IN_USE, END_TRIP, AVAILABLE, Set.of(RIDER, SYSTEM)),
            new Transition(AVAILABLE, SEND_TO_MAINTENANCE, IN_MAINTENANCE,
                    Set.of(SYSTEM, FLEET_OPERATOR, MAINTENANCE_TECHNICIAN)),
            new Transition(IN_USE, SEND_TO_MAINTENANCE, IN_MAINTENANCE, Set.of(SYSTEM, FLEET_OPERATOR)),
            new Transition(IN_MAINTENANCE, COMPLETE_REPAIR, AVAILABLE, Set.of(MAINTENANCE_TECHNICIAN)),
            new Transition(AVAILABLE, RETIRE, RETIRED, Set.of(FLEET_OPERATOR)),
            new Transition(IN_MAINTENANCE, RETIRE, RETIRED, Set.of(FLEET_OPERATOR, MAINTENANCE_TECHNICIAN))
    );

    private final Map<BikeState, Map<LifecycleEvent, Transition>> table = new EnumMap<>(BikeState.class);

    public BikeLifecycleStateMachine() {
        for (BikeState state : BikeState.values()) {
            table.put(state, new EnumMap<>(LifecycleEvent.class));
        }
        for (Transition t : TRANSITIONS) {
            Transition previous = table.get(t.from()).put(t.event(), t);
            if (previous != null) {
                throw new IllegalStateException("Duplicate transition " + t.from() + " + " + t.event());
            }
            if (t.from().isTerminal()) {
                throw new IllegalStateException("A final state cannot have outgoing transitions: " + t);
            }
        }
    }

    /**
     * Valida e devolve o próximo estado.
     *
     * @throws InvalidTransitionException       se o evento não existe a partir de {@code current}
     * @throws TransitionNotPermittedException  se existe mas {@code role} não o pode pedir
     */
    public BikeState next(BikeState current, LifecycleEvent event, ActorRole role) {
        Transition transition = find(current, event)
                .orElseThrow(() -> new InvalidTransitionException(current, event, allowedEvents(current)));
        if (!transition.isAllowedFor(role)) {
            throw new TransitionNotPermittedException(current, event, role);
        }
        return transition.to();
    }

    public Optional<Transition> find(BikeState current, LifecycleEvent event) {
        return Optional.ofNullable(table.get(current).get(event));
    }

    /** Eventos possíveis a partir de um estado, para qualquer papel. */
    public Set<LifecycleEvent> allowedEvents(BikeState current) {
        Set<LifecycleEvent> events = EnumSet.noneOf(LifecycleEvent.class);
        events.addAll(table.get(current).keySet());
        return events;
    }

    /** Eventos que um papel pode pedir a partir de um estado (a interface usa isto para mostrar os botões certos). */
    public Set<LifecycleEvent> allowedEvents(BikeState current, ActorRole role) {
        Set<LifecycleEvent> events = EnumSet.noneOf(LifecycleEvent.class);
        table.get(current).forEach((event, t) -> {
            if (t.isAllowedFor(role)) {
                events.add(event);
            }
        });
        return events;
    }
}
