package pt.ua.kinetiq.fleet.lifecycle.events;

import pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeState;
import pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent;

/**
 * Mensagem publicada no tópico fleet.events.status sempre que uma bicicleta muda de estado.
 * Consumidores: WebSocket Service (mapa e dashboards ao vivo), módulos de BI/ML.
 * Contrato: docs/schemas/bike-state-changed-schema.json.
 */
public record BikeStateChangedEvent(
        String schemaVersion,
        String eventId,
        String bikeId,
        long timestamp,
        BikeState previousState,
        BikeState newState,
        LifecycleEvent trigger,
        ActorRole actorRole,
        String actorId,
        String reason
) {
    public static final String SCHEMA_VERSION = "1.0";
}
