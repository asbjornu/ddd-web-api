package no.javazone.elevator.shared.domain;

/**
 * A landing call: a rider at a floor requesting the car in a direction.
 * Distinct from a car call (a destination selected from inside the
 * car, arriving with slice 3) -- see
 * {@code docs/architecture.md}'s "Core workflows, as commands" section.
 */
public record LandingCall(Floor floor, Direction direction) {
}
