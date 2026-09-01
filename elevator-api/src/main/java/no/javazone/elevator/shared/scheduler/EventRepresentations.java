package no.javazone.elevator.shared.scheduler;

import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorStateNames;
import no.javazone.elevator.shared.hypermedia.Representation;

/**
 * The SSE payload shape schedulers publish after a scheduled transition
 * -- factored out so {@link MovementScheduler} and {@link DoorScheduler}
 * do not each rebuild the same handful of properties from an
 * {@link Elevator}.
 */
final class EventRepresentations {

    private EventRepresentations() {
    }

    static Representation of(Elevator elevator, ElevatorProperties properties) {
        return Representation.builder("Elevator")
                .property("currentFloor", elevator.currentFloor().level())
                .property("state", ElevatorStateNames.of(elevator.state()))
                .property("direction", ElevatorStateNames.directionOf(elevator.state()))
                .property("doorPosition", elevator.doors().position().name().toLowerCase())
                .property("obstructed", elevator.doors().obstructed())
                .property("weightKg", elevator.load().kilograms())
                .property("capacityKg", elevator.load().capacityKilograms())
                .property("destinationFloor", ElevatorStateNames.destinationOf(elevator.state()))
                .property("travelSecondsPerFloor", properties.travelSecondsPerFloor())
                .build();
    }
}
