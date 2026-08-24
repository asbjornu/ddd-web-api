package no.javazone.elevator.shared.domain;

import java.time.Instant;

/** The obstruction was cleared -- the doors may now be closed again. */
public record ObstructionCleared(ElevatorId elevatorId, Instant occurredAt) implements DomainEvent {
}
