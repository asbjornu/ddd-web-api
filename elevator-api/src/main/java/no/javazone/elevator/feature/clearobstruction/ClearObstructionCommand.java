package no.javazone.elevator.feature.clearobstruction;

import no.javazone.elevator.shared.domain.ElevatorId;

/** A rider's intention: clear a simulated obstruction. */
public record ClearObstructionCommand(ElevatorId elevatorId) {
}
