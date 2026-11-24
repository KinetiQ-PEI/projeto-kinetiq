package pt.ua.kinetiq.fleet.lifecycle.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BikeStateTransitionRepository extends JpaRepository<BikeStateTransition, UUID> {

    /** Histórico de uma bicicleta, do mais recente para o mais antigo. */
    List<BikeStateTransition> findByBikeIdOrderByOccurredAtDesc(String bikeId);
}
