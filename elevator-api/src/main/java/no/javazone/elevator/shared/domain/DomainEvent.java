package no.javazone.elevator.shared.domain;

import java.time.Instant;

/**
 * Marker for the domain event hierarchy: a fact that was stored, in the
 * past tense, distinct from a command (an intention, imperative) and a
 * view (a projection, present tense) -- see {@code docs/architecture.md}'s
 * "CQRS and domain events" section and the Event Modeling vocabulary it
 * follows.
 *
 * <p>Not sealed yet: no event types exist until the slice that emits them
 * lands (starting with {@code ElevatorCalled} in slice 2). Each slice
 * that adds an event type extends this interface; nothing here names any
 * of them in advance, which is deliberately the opposite of Speculative
 * Generality.
 */
public interface DomainEvent {

    Instant occurredAt();
}
