package no.javazone.elevator.shared.domain;

import java.time.Instant;

/** The doors finished closing and the car returned to idle. */
public record DoorsClosed(ElevatorId elevatorId, Instant occurredAt) implements DomainEvent {
}
