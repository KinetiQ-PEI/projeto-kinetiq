package pt.ua.kinetiq.fleet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import pt.ua.kinetiq.fleet.lifecycle.domain.BikeLifecycleStateMachine;

import java.time.Clock;

@SpringBootApplication
public class FleetManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetManagementApplication.class, args);
    }

    @Bean
    public BikeLifecycleStateMachine bikeLifecycleStateMachine() {
        return new BikeLifecycleStateMachine();
    }

    /** Relógio injetável: nos testes usa-se um relógio fixo. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
