package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The car committed to travelling from {@code from} to {@code to}. Not
 * itself a command's direct output in the rider's vocabulary -- it is
 * what {@link Elevator#call} or {@link Elevator#selectFloor} produce
 * <em>in addition to</em> their own event when the car was idle and had
 * somewhere to go. {@code shared.scheduler} listens for this to compute
 * when {@link FloorReached} should fire -- see
 * {@code docs/architecture.md}'s "CQRS and domain events" section on
 * why state is derived forward from a scheduled instant rather than
 * backward from elapsed time.
 */
public record MovementStarted(
        ElevatorId elevatorId, Floor from, Floor to, Direction direction, Instant departedAt)
        implements DomainEvent {

    @Override
    public Instant occurredAt() {
        return departedAt;
    }
}
