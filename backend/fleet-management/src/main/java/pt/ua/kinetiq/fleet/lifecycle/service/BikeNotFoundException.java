package pt.ua.kinetiq.fleet.lifecycle.service;

public class BikeNotFoundException extends RuntimeException {

    public BikeNotFoundException(String bikeId) {
        super("Bike " + bikeId + " is not registered");
    }
}
