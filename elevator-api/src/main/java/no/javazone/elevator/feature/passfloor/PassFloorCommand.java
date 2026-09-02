package no.javazone.elevator.feature.passfloor;

import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.Floor;

/**
 * Sensor telemetry: the simulated hoistway vane and leveling-switch
 * pair reports that the car has reached {@code floor} while
 * travelling -- see {@code shared.scheduler.MovementScheduler}, this
 * command's only caller, standing in for the sensor hardware a real
 * car would have (see {@link no.javazone.elevator.shared.domain.FloorPassed}'s
 * own Javadoc). Never issued over HTTP, and deliberately has no
 * controller or affordance: nothing outside the scheduler has any
 * business claiming a floor was physically passed, so nothing outside
 * it is given a way to.
 */
public record PassFloorCommand(ElevatorId elevatorId, Floor floor) {
}
