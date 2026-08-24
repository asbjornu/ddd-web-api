package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The car arrived at {@code floor}, the scheduler's own output rather
 * than a rider's -- see {@link MovementStarted}. Produced by
 * {@link Elevator#arrive}, never by a rider-facing command.
 */
public record FloorReached(ElevatorId elevatorId, Floor floor, Instant occurredAt)
        implements DomainEvent {
}
