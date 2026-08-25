package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The car arrived at the recall floor and settled into {@code
 * outOfService} -- either immediately, if {@link EmergencyRecallTriggered}
 * found it already there, or once {@code shared.scheduler}'s recall
 * scheduler completed the travel it started.
 */
public record EmergencyRecallCompleted(ElevatorId elevatorId, Instant occurredAt)
        implements DomainEvent {
}
