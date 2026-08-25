package no.javazone.elevator.shared.hypermedia;

import java.util.Optional;
import no.javazone.elevator.shared.security.Principal;

/**
 * What an {@link AffordanceContributor} may condition its answer on:
 * which elevator (if any -- the entry point has none), its current
 * state (a lower camelCase string, not the write-side
 * {@code ElevatorState} sealed type -- the query side has no reason to
 * depend on the write side just to compute this), whether its doors are
 * obstructed, whether the car is overloaded, and the caller's
 * {@link Principal} -- always present, never {@code null}, {@link
 * Principal#ANONYMOUS} standing in for an unauthenticated caller. A
 * privileged affordance (see {@code feature.entermaintenance}) checks
 * {@code principal().hasScope(...)} the same way it checks state:
 * authority and availability answered by one predicate, per {@code
 * docs/architecture.md}'s "Key-switch and authorization" section.
 */
public record AffordanceContext(
        Optional<String> elevatorSegment,
        Optional<String> state,
        Optional<Boolean> obstructed,
        Optional<Boolean> overloaded,
        Principal principal) {

    public static AffordanceContext root() {
        return root(Principal.ANONYMOUS);
    }

    public static AffordanceContext root(Principal principal) {
        return new AffordanceContext(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), principal);
    }

    public static AffordanceContext forElevator(
            String segment, String state, boolean obstructed, boolean overloaded) {
        return forElevator(segment, state, obstructed, overloaded, Principal.ANONYMOUS);
    }

    public static AffordanceContext forElevator(
            String segment, String state, boolean obstructed, boolean overloaded,
            Principal principal) {
        return new AffordanceContext(
                Optional.of(segment), Optional.of(state), Optional.of(obstructed),
                Optional.of(overloaded), principal);
    }
}
