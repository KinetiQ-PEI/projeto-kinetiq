package pt.ua.kinetiq.fleet.lifecycle.api;

import pt.ua.kinetiq.fleet.lifecycle.domain.BikeState;
import pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent;

import java.time.Instant;
import java.util.Set;

/**
 * Estado atual e o que se pode fazer a seguir. A dashboard e a app usam {@code allowedEvents}
 * para mostrar só os botões válidos (ex.: "Retire bike" só aparece ao operador quando é permitido).
 */
public record BikeStateResponse(
        String bikeId,
        BikeState state,
        Instant stateChangedAt,
        Set<LifecycleEvent> allowedEvents
) {
}
