package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The doors started closing -- from an explicit {@code CloseDoors}, or
 * automatically once the open-door timeout elapses. Not yet fully
 * closed: obstruction can still re-open them before
 * {@link DoorsClosed} fires.
 */
public record DoorsClosingStarted(ElevatorId elevatorId, Instant occurredAt)
        implements DomainEvent {
}
