package no.javazone.elevator.feature.obstructdoors;

import no.javazone.elevator.shared.domain.ElevatorId;

/** The simulated obstruction sensor: something is blocking the closing doors. */
public record ObstructDoorsCommand(ElevatorId elevatorId) {
}
