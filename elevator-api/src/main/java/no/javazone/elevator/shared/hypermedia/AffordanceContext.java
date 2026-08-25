package no.javazone.elevator.shared.hypermedia;

import java.util.Optional;

/**
 * What an {@link AffordanceContributor} may condition its answer on:
 * which elevator (if any -- the entry point has none), its current
 * state (a lower camelCase string, not the write-side
 * {@code ElevatorState} sealed type -- the query side has no reason to
 * depend on the write side just to compute this), whether its doors are
 * obstructed, and whether the car is overloaded.
 *
 * <p>No {@code Principal} yet: authority joins this context in slice 6,
 * once one exists to pass -- see {@code docs/architecture.md}'s
 * "Domain-driven security" section for why it belongs in this same
 * predicate once it does.
 */
public record AffordanceContext(
        Optional<String> elevatorSegment,
        Optional<String> state,
        Optional<Boolean> obstructed,
        Optional<Boolean> overloaded) {

    public static AffordanceContext root() {
        return new AffordanceContext(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static AffordanceContext forElevator(
            String segment, String state, boolean obstructed, boolean overloaded) {
        return new AffordanceContext(
                Optional.of(segment), Optional.of(state), Optional.of(obstructed),
                Optional.of(overloaded));
    }
}
