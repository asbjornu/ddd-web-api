package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * Every pending landing and car call was discarded -- entering
 * maintenance or (a future slice's job) an emergency recall, the two
 * commands that pre-empt whatever a rider had already queued.
 * {@code reason} names which: {@code "maintenance"} here.
 */
public record PendingRequestsCleared(ElevatorId elevatorId, String reason, Instant occurredAt)
        implements DomainEvent {
}
