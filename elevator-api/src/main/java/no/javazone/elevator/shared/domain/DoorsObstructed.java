package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The closing doors met an obstruction and re-opened -- the simulated
 * sensor's own event. See {@code docs/architecture.md}'s "Core
 * workflows, as commands" section: there is no real obstruction sensor,
 * so this is triggered by a rider action ({@code ObstructDoors}) rather
 * than hardware.
 */
public record DoorsObstructed(ElevatorId elevatorId, Instant occurredAt) implements DomainEvent {
}
