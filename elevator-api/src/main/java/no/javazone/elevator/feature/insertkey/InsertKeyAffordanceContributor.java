package no.javazone.elevator.feature.insertkey;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import org.springframework.stereotype.Component;

/**
 * Offers {@code insert-key} to any caller holding no scope at all --
 * present "like any other" affordance, per {@code docs/plan.html}'s
 * "Following the key switch is an authorization challenge" section.
 * Absent once a caller already holds a scope: there is nothing more to
 * gain from following it again, and {@code enter-maintenance}/{@code
 * exit-maintenance} are the affordances that then appear in its place.
 */
@Component
public class InsertKeyAffordanceContributor implements AffordanceContributor {

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        if (context.principal().hasAnyScope()) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get() + "/key-switch";
        return List.of(new Affordance("insert-key", "Insert technician key", "POST", href));
    }
}
