package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The elevator was returned to service by an explicit {@code
 * ExitMaintenance} command -- the only way out of {@code outOfService},
 * whether it was reached via {@code EnterMaintenance} or automatically
 * via a completed emergency recall.
 */
public record MaintenanceExited(ElevatorId elevatorId, Instant occurredAt) implements DomainEvent {
}
