package no.javazone.elevator.shared.hypermedia;

import java.util.List;

/**
 * Implemented by exactly one class per slice: the thing that knows
 * whether that slice's one command is legal right now, for this caller.
 * {@link AffordanceCatalog} collects every bean implementing this
 * interface -- a slice contributes an affordance by existing, not by
 * registering itself anywhere.
 *
 * <p>Authority joins {@link AffordanceContext} as a {@code Principal},
 * always present -- a privileged contributor (see
 * {@code feature.entermaintenance}) checks {@code
 * context.principal().hasScope(...)} the same way every contributor
 * already checks {@code context.state()}: one predicate answering both
 * "is this allowed" and "is this available right now", per {@code
 * docs/architecture.md}'s "Key-switch and authorization" section.
 */
public interface AffordanceContributor {

    List<Affordance> contribute(AffordanceContext context);
}
