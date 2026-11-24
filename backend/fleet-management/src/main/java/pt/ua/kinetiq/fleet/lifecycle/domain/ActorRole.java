package pt.ua.kinetiq.fleet.lifecycle.domain;

/**
 * Quem pede a transição. Os três primeiros são os papéis do Keycloak (RF24);
 * SYSTEM é usado pelos serviços internos (ex.: um alerta crítico do módulo de ML).
 */
public enum ActorRole {
    RIDER,
    FLEET_OPERATOR,
    MAINTENANCE_TECHNICIAN,
    SYSTEM
}
