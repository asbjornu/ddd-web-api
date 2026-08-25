package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The elevator was taken out of service -- either directly, via an
 * explicit {@code EnterMaintenance} command from a technician holding
 * the key switch, or automatically, once an emergency recall completes
 * (a future slice's job). {@code reason} distinguishes the two:
 * {@code "keySwitch"} for the former.
 */
public record MaintenanceEntered(ElevatorId elevatorId, String reason, Instant occurredAt)
        implements DomainEvent {
}
