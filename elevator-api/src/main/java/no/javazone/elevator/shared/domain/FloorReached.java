package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The car arrived at {@code floor} -- {@code floor} is specifically the
 * car's own destination, not merely a floor it passed through; see
 * {@link FloorPassed}, which fires for every floor along the route and
 * fires alongside this event when the two coincide. The scheduler's own
 * output rather than a rider's, same as {@link FloorPassed} -- see
 * {@link MovementStarted}. Produced by {@link Elevator#passFloor}, never
 * by a rider-facing command.
 */
public record FloorReached(ElevatorId elevatorId, Floor floor, Instant occurredAt)
        implements DomainEvent {
}
