package pt.ua.kinetiq.fleet.lifecycle.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeLifecycleStateMachine;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeState;
import pt.ua.kinetiq.fleet.lifecycle.domain.InvalidTransitionException;
import pt.ua.kinetiq.fleet.lifecycle.domain.LifecycleEvent;
import pt.ua.kinetiq.fleet.lifecycle.domain.TransitionNotPermittedException;
import pt.ua.kinetiq.fleet.lifecycle.persistence.Bike;
import pt.ua.kinetiq.fleet.lifecycle.service.BikeLifecycleService;
import pt.ua.kinetiq.fleet.lifecycle.service.BikeNotFoundException;

import java.time.Instant;
import java.util.EnumSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BikeLifecycleController.class)
class BikeLifecycleControllerTest {

    private static final String BIKE = "MB-2026-042";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BikeLifecycleService service;

    private final BikeLifecycleStateMachine fsm = new BikeLifecycleStateMachine();

    @Test
    void getStateReturnsStateAndAllowedEventsForRole() throws Exception {
        when(service.getBike(BIKE)).thenReturn(new Bike(BIKE, Instant.parse("2026-11-22T10:00:00Z")));
        when(service.allowedEvents(BikeState.AVAILABLE, ActorRole.RIDER))
                .thenReturn(fsm.allowedEvents(BikeState.AVAILABLE, ActorRole.RIDER));

        mvc.perform(get("/bikes/{id}/state", BIKE).param("role", "RIDER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bikeId").value(BIKE))
                .andExpect(jsonPath("$.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.allowedEvents[0]").value("START_TRIP"));
    }

    @Test
    void validTransitionReturnsNewState() throws Exception {
        Bike bike = new Bike(BIKE, Instant.parse("2026-11-22T10:00:00Z"));
        bike.changeState(BikeState.IN_USE, Instant.parse("2026-11-22T10:05:00Z"));
        when(service.applyEvent(eq(BIKE), eq(LifecycleEvent.START_TRIP), eq(ActorRole.RIDER), any(), any()))
                .thenReturn(bike);
        when(service.allowedEvents(BikeState.IN_USE, ActorRole.RIDER))
                .thenReturn(EnumSet.of(LifecycleEvent.END_TRIP));

        mvc.perform(post("/bikes/{id}/transitions", BIKE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event": "START_TRIP", "actorRole": "RIDER", "actorId": "rider-17"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_USE"))
                .andExpect(jsonPath("$.allowedEvents[0]").value("END_TRIP"));
    }

    @Test
    void invalidTransitionReturns409WithAllowedEvents() throws Exception {
        when(service.applyEvent(eq(BIKE), eq(LifecycleEvent.START_TRIP), any(), any(), any()))
                .thenThrow(new InvalidTransitionException(BikeState.IN_MAINTENANCE, LifecycleEvent.START_TRIP,
                        fsm.allowedEvents(BikeState.IN_MAINTENANCE)));

        mvc.perform(post("/bikes/{id}/transitions", BIKE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event": "START_TRIP", "actorRole": "RIDER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid state transition"))
                .andExpect(jsonPath("$.currentState").value("IN_MAINTENANCE"))
                .andExpect(jsonPath("$.allowedEvents.length()").value(2));
    }

    @Test
    void roleWithoutPermissionReturns403() throws Exception {
        when(service.applyEvent(eq(BIKE), eq(LifecycleEvent.RETIRE), eq(ActorRole.RIDER), any(), any()))
                .thenThrow(new TransitionNotPermittedException(BikeState.AVAILABLE, LifecycleEvent.RETIRE, ActorRole.RIDER));

        mvc.perform(post("/bikes/{id}/transitions", BIKE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event": "RETIRE", "actorRole": "RIDER"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.role").value("RIDER"));
    }

    @Test
    void unknownBikeReturns404() throws Exception {
        when(service.getBike("MB-2026-999")).thenThrow(new BikeNotFoundException("MB-2026-999"));

        mvc.perform(get("/bikes/{id}/state", "MB-2026-999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingEventReturns400() throws Exception {
        mvc.perform(post("/bikes/{id}/transitions", BIKE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actorRole": "RIDER"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEventNameReturns400() throws Exception {
        mvc.perform(post("/bikes/{id}/transitions", BIKE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"event": "FLY_AWAY", "actorRole": "RIDER"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
