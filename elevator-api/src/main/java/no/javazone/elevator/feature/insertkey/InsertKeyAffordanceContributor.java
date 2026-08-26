package no.javazone.elevator.feature.insertkey;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code insert-key} to any caller holding no scope at all --
 * present "like any other" affordance, per {@code docs/plan.html}'s
 * "Following the key switch is an authorization challenge" section.
 * Absent once a caller already holds a scope: there is nothing more to
 * gain from following it again, and {@code enter-maintenance}/{@code
 * exit-maintenance} are the affordances that then appear in its place.
 * Also absent, for every caller, while an emergency recall is in
 * transit -- recall pre-empts everything, per {@code docs/plan.html}'s
 * "Elevator resource" specification ("offers nothing to anyone").
 *
 * <p>Points at {@link KeySwitchSessionController}, not a separate
 * discovery-only URL: the one {@code secret} field it declares is what
 * a caller who already knows the scheme (elevator-ui, or a repeat
 * machine client) posts to complete the exchange in one round trip;
 * a caller who posts nothing there yet -- the RFC 9728 discovery case
 * -- gets exactly the same 401 challenge either way.
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
        if ("emergencyRecall".equals(context.state().orElse(""))) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get() + "/key-switch/session";
        return List.of(new Affordance(
                "insert-key", "Insert technician key", "POST", href,
                List.of(Field.password("secret", ""))));
    }
}
