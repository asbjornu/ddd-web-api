package no.javazone.elevator.feature.autoclosedoors;

import no.javazone.elevator.shared.domain.ElevatorId;

/**
 * The door-open timer reports that the doors have been open long
 * enough to close automatically -- see {@code
 * shared.scheduler.DoorScheduler}, this command's only caller, and
 * {@link no.javazone.elevator.shared.domain.Elevator#autoCloseIfStillOpen}'s
 * own Javadoc for the guard this command is still subject to (silently
 * does nothing if the doors are obstructed, overloaded, or already
 * closed some other way). Never issued over HTTP, and deliberately has
 * no controller or affordance: nothing outside the scheduler has any
 * business claiming the open-door timeout has elapsed.
 */
public record AutoCloseDoorsCommand(ElevatorId elevatorId) {
}
