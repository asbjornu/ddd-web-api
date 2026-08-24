package no.javazone.elevator.shared.domain;

/**
 * A rider inside the car selected a destination floor. Distinct from a
 * {@link LandingCall}: no direction, and it can only be added from
 * inside the car -- see {@code docs/architecture.md}'s "Core workflows,
 * as commands" section.
 */
public record CarCall(Floor floor) {
}
