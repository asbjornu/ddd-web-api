package no.javazone.elevator.feature.selectfloor;

import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.Floor;

/**
 * A rider's intention: select {@code floor} as a destination from
 * inside the car.
 */
public record SelectFloorCommand(ElevatorId elevatorId, Floor floor) {
}
