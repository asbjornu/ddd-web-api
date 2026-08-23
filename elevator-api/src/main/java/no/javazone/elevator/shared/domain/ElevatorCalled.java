package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * A rider at {@code floor} requested the car, heading {@code direction}
 * -- the first event this system produces, and the reason
 * {@link RequestQueue} exists: a landing call is queued and scheduled
 * relative to other pending calls, not a direct state assignment. See
 * {@code docs/architecture.md}'s "Core workflows, as commands" section.
 */
public record ElevatorCalled(
        ElevatorId elevatorId, Floor floor, Direction direction, Instant occurredAt)
        implements DomainEvent {
}
