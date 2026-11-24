package pt.ua.kinetiq.fleet.lifecycle.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.ua.kinetiq.fleet.lifecycle.domain.ActorRole;
import pt.ua.kinetiq.fleet.lifecycle.persistence.Bike;
import pt.ua.kinetiq.fleet.lifecycle.service.BikeLifecycleService;

import java.util.List;

/**
 * Endpoints do ciclo de vida (chamados pela Core API / API Gateway).
 *
 * <ul>
 *   <li>GET  /bikes/{bikeId}/state[?role=...]  estado atual e eventos permitidos</li>
 *   <li>POST /bikes/{bikeId}/transitions       pede uma mudança de estado</li>
 *   <li>GET  /bikes/{bikeId}/transitions       histórico de estados</li>
 * </ul>
 */
@RestController
@RequestMapping("/bikes/{bikeId}")
public class BikeLifecycleController {

    private final BikeLifecycleService service;

    public BikeLifecycleController(BikeLifecycleService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public BikeStateResponse getState(@PathVariable String bikeId,
                                      @RequestParam(required = false) ActorRole role) {
        Bike bike = service.getBike(bikeId);
        return toResponse(bike, role);
    }

    @PostMapping("/transitions")
    public BikeStateResponse transition(@PathVariable String bikeId,
                                        @Valid @RequestBody TransitionRequest request) {
        Bike bike = service.applyEvent(bikeId, request.event(), request.actorRole(),
                request.actorId(), request.reason());
        return toResponse(bike, request.actorRole());
    }

    @GetMapping("/transitions")
    public List<TransitionHistoryItem> history(@PathVariable String bikeId) {
        return service.getHistory(bikeId).stream().map(TransitionHistoryItem::from).toList();
    }

    private BikeStateResponse toResponse(Bike bike, ActorRole role) {
        return new BikeStateResponse(bike.getBikeId(), bike.getState(), bike.getStateChangedAt(),
                service.allowedEvents(bike.getState(), role));
    }
}
