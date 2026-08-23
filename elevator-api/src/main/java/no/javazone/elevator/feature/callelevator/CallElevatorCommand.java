package no.javazone.elevator.feature.callelevator;

import no.javazone.elevator.shared.domain.Direction;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.Floor;

/**
 * A rider's intention: call the car to {@code floor}, heading
 * {@code direction}. A message ("do this"), not a field assignment --
 * see {@code docs/plan.html} &sect;6.
 */
public record CallElevatorCommand(ElevatorId elevatorId, Floor floor, Direction direction) {
}
