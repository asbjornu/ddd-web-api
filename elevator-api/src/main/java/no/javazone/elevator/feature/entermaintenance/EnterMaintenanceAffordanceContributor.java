package no.javazone.elevator.feature.entermaintenance;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code enter-maintenance} only to a caller holding {@code
 * elevator:maintenance}, and only while the car is not already {@code
 * outOfService} or mid-recall -- authority and availability, the same
 * predicate, per {@code docs/architecture.md}'s "Key-switch and
 * authorization" section. A caller without the scope never learns this
 * operation exists at all; see {@code
 * EnterMaintenanceController}'s Javadoc for what happens if they invoke
 * it anyway.
 */
@Component
public class EnterMaintenanceAffordanceContributor implements AffordanceContributor {

    private static final List<String> UNAVAILABLE_STATES = List.of("outOfService", "emergencyRecall");

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        if (!context.principal().hasScope("elevator:maintenance")) {
            return List.of();
        }
        String state = context.state().orElse("");
        if (UNAVAILABLE_STATES.contains(state)) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get();
        return List.of(new Affordance(
                "enter-maintenance", "Enter maintenance", "POST", href,
                List.of(Field.hidden("type", "EnterMaintenance"))));
    }
}
