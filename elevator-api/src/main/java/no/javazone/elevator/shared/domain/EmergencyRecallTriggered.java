package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * A technician (or, in principle, the building's fire alarm panel --
 * see {@code docs/plan.html}'s "Why two scopes rather than one")
 * triggered an emergency recall. Carries {@code fromFloor} alongside
 * {@code recallFloor} so {@code shared.scheduler}'s recall scheduler
 * can tell, without re-fetching the aggregate, whether the car was
 * already at the recall floor (no travel, {@link EmergencyRecallCompleted}
 * follows immediately) or has somewhere to go first.
 */
public record EmergencyRecallTriggered(
        ElevatorId elevatorId, Floor fromFloor, Floor recallFloor, Instant occurredAt)
        implements DomainEvent {
}
