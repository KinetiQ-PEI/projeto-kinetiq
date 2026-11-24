package pt.ua.kinetiq.fleet.lifecycle.domain;

/**
 * Estados do ciclo de vida de uma bicicleta (RF06).
 * Os nomes são os mesmos do enum "status" em docs/schemas/telemetry-schema.json.
 */
public enum BikeState {
    AVAILABLE,
    IN_USE,
    IN_MAINTENANCE,
    RETIRED;

    /** Um estado final não tem saída: uma bicicleta retirada não volta à frota. */
    public boolean isTerminal() {
        return this == RETIRED;
    }
}
