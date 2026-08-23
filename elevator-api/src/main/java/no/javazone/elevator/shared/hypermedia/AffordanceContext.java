package no.javazone.elevator.shared.hypermedia;

import java.util.Optional;

/**
 * What an {@link AffordanceContributor} may condition its answer on:
 * which elevator (if any -- the entry point has none) and its current
 * state, expressed the same way the read model already does (a lower
 * camelCase string, not the write-side {@code ElevatorState} sealed
 * type -- the query side has no reason to depend on the write side just
 * to compute this).
 *
 * <p>No {@code Principal} yet: authority joins this context in slice 6,
 * once one exists to pass -- see {@code docs/architecture.md}'s
 * "Domain-driven security" section for why it belongs in this same
 * predicate once it does.
 */
public record AffordanceContext(Optional<String> elevatorSegment, Optional<String> state) {

    public static AffordanceContext root() {
        return new AffordanceContext(Optional.empty(), Optional.empty());
    }

    public static AffordanceContext forElevator(String segment, String state) {
        return new AffordanceContext(Optional.of(segment), Optional.of(state));
    }
}
