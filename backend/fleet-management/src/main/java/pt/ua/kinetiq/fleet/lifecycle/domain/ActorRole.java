package pt.ua.kinetiq.fleet.lifecycle.domain;

/**
 * Quem pede a transição. Os quatro primeiros são os papéis do Keycloak (RF24);
 * SYSTEM é usado pelos serviços internos (ex.: um alerta crítico do módulo de ML).
 * FLEET_ANALYST só consulta: não pode pedir nenhuma transição.
 */
public enum ActorRole {
    RIDER,
    FLEET_OPERATOR,
    MAINTENANCE_TECHNICIAN,
    FLEET_ANALYST,
    SYSTEM
}
