package no.javazone.elevator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Building/elevator configuration. See docs/architecture.md's "Building
 * and elevators" and "Timing" sections for the reasoning behind these
 * defaults.
 */
@ConfigurationProperties(prefix = "elevator")
public record ElevatorProperties(
        int floors,
        int recallFloor,
        int travelSecondsPerFloor,
        int doorOpenTimeoutSeconds,
        int weightCapacityKg,
        String technicianKey) {
}
