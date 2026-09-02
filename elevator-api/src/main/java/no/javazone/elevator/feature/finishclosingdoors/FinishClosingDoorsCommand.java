package no.javazone.elevator.feature.finishclosingdoors;

import no.javazone.elevator.shared.domain.ElevatorId;

/**
 * The door-close timer reports that the closing duration has elapsed --
 * see {@code shared.scheduler.DoorScheduler}, this command's only
 * caller, and {@link
 * no.javazone.elevator.shared.domain.Elevator#finishClosingIfStillClosing}'s
 * own Javadoc for the guard this command is still subject to (silently
 * does nothing if an obstruction already re-opened the doors in the
 * meantime). Never issued over HTTP, and deliberately has no controller
 * or affordance: nothing outside the scheduler has any business
 * claiming the close-door duration has elapsed.
 */
public record FinishClosingDoorsCommand(ElevatorId elevatorId) {
}
