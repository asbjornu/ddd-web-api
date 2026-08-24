package no.javazone.elevator.feature.opendoors;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code open-doors} whenever the car is not moving and not out
 * of service/mid-recall -- present even while already open (an
 * idempotent no-op {@link no.javazone.elevator.shared.domain.Elevator#openDoors}
 * itself permits) or while closing (reopens them), absent only where
 * the command itself refuses.
 */
@Component
public class OpenDoorsAffordanceContributor implements AffordanceContributor {

    private static final List<String> UNAVAILABLE_STATES =
            List.of("movingUp", "movingDown", "outOfService", "emergencyRecall");

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        String state = context.state().orElse("");
        if (UNAVAILABLE_STATES.contains(state)) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get();
        return List.of(new Affordance(
                "open-doors", "Open doors", "POST", href,
                List.of(Field.hidden("type", "OpenDoors"))));
    }
}
