package pt.ua.kinetiq.fleet.lifecycle.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole;
import pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent;

/**
 * Corpo de POST /bikes/{bikeId}/transitions.
 *
 * <p>Por agora o papel e o id de quem pede vêm no corpo. Quando o Keycloak e a API Gateway
 * estiverem ligados, passam a ser lidos do token JWT e estes dois campos deixam de ser aceites.
 */
public record TransitionRequest(
        @NotNull LifecycleEvent event,
        @NotNull ActorRole actorRole,
        @Size(max = 64) String actorId,
        @Size(max = 500) String reason
) {
}
