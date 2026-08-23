package no.javazone.elevator.shared.hypermedia;

import java.util.List;

/**
 * Implemented by exactly one class per slice: the thing that knows
 * whether that slice's one command is legal right now, for this caller.
 * {@link AffordanceCatalog} collects every bean implementing this
 * interface -- a slice contributes an affordance by existing, not by
 * registering itself anywhere.
 *
 * <p>No {@code Principal} parameter yet: authority joins
 * {@link AffordanceContext} in slice 6, once one exists to pass -- see
 * {@code docs/architecture.md}'s "Domain-driven security" reasoning for
 * why authority belongs in this same predicate once there is one.
 */
public interface AffordanceContributor {

    List<Affordance> contribute(AffordanceContext context);
}
