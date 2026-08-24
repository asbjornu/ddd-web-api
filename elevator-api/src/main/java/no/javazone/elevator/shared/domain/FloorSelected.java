package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * A rider selected {@code floor} as a destination from inside the car
 * -- the car-call counterpart to {@link ElevatorCalled}.
 */
public record FloorSelected(ElevatorId elevatorId, Floor floor, Instant occurredAt)
        implements DomainEvent {
}
