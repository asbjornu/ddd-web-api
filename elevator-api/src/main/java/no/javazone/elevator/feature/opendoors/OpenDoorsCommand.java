package no.javazone.elevator.feature.opendoors;

import no.javazone.elevator.shared.domain.ElevatorId;

/** A rider's intention: open the doors. */
public record OpenDoorsCommand(ElevatorId elevatorId) {
}
