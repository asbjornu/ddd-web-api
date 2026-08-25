package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The simulated weight sensor reported {@code weightKg} -- telemetry,
 * not a rider's intention (there is no "weigh the car" button; a rider
 * sets a slider, which is exactly what this event says happened). See
 * {@code docs/architecture.md}'s "Core workflows, as commands" section.
 */
public record LoadReported(ElevatorId elevatorId, int weightKg, Instant occurredAt)
        implements DomainEvent {
}
