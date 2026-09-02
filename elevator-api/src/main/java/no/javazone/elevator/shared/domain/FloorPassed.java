package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * The car passed {@code floor} while travelling -- fired for every
 * floor along the route, including the destination, mirroring what a
 * real hoistway's floor vanes report continuously rather than once at
 * the end of a trip (see {@code docs/architecture.md}'s "Domain model"
 * section). {@link FloorReached} fires <em>in addition to</em> this
 * event only when {@code floor} is where the car is actually stopping.
 */
public record FloorPassed(ElevatorId elevatorId, Floor floor, Instant occurredAt)
        implements DomainEvent {
}
