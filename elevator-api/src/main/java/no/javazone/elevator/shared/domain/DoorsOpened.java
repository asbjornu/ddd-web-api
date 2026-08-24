package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * Doors opened -- whether from an explicit {@code OpenDoors} command or
 * because the car just arrived (see {@link Elevator#arrive}). Either
 * way, {@code shared.scheduler}'s door scheduler reacts the same: the
 * auto-close timer starts.
 */
public record DoorsOpened(ElevatorId elevatorId, Instant occurredAt) implements DomainEvent {
}
