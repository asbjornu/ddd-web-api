package no.javazone.elevator.feature.insertkey;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import org.springframework.stereotype.Component;

/**
 * Offers {@code withdraw-key} to any caller holding a scope --
 * {@code insert-key}'s mirror image, present exactly when it is absent
 * (a caller either has not turned the key yet, or has and may turn it
 * back). Points at the same {@link KeySwitchSessionController}
 * {@code insert-key} does, {@code DELETE} rather than {@code POST}: no
 * client -- elevator-ui included -- constructs this URL itself, any
 * more than it constructs any other affordance's.
 */
@Component
public class WithdrawKeyAffordanceContributor implements AffordanceContributor {

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        if (!context.principal().hasAnyScope()) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get() + "/key-switch/session";
        return List.of(new Affordance("withdraw-key", "Withdraw technician key", "DELETE", href));
    }
}
