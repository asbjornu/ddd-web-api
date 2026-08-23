package no.javazone.elevator.shared.hypermedia;

import java.util.List;

/**
 * Implemented by exactly one class per slice: the thing that knows
 * whether that slice's one command is legal right now, for this caller.
 * {@link AffordanceCatalog} collects every bean implementing this
 * interface -- a slice contributes an affordance by existing, not by
 * registering itself anywhere.
 *
 * <p>No context parameter yet: the entry point (slice 0) has no
 * per-resource state to condition on. A later slice that needs one (the
 * aggregate, the caller's {@code Principal}) will evolve this interface
 * when it exists -- see {@code docs/architecture.md}'s "Domain-driven
 * security" reasoning for why authority belongs in that same predicate
 * once there is a {@code Principal} to pass it.
 */
public interface AffordanceContributor {

    List<Affordance> contribute();
}
