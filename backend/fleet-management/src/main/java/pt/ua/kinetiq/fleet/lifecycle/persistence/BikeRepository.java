package pt.ua.kinetiq.fleet.lifecycle.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BikeRepository extends JpaRepository<Bike, String> {
}
