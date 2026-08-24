package no.javazone.elevator.feature.closedoors;

import no.javazone.elevator.shared.domain.ElevatorId;

/** A rider's intention: close the doors. */
public record CloseDoorsCommand(ElevatorId elevatorId) {
}
