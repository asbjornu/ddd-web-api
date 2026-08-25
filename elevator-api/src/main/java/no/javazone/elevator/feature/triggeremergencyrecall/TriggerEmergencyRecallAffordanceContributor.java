package no.javazone.elevator.feature.triggeremergencyrecall;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code trigger-emergency-recall} to any caller holding {@code
 * elevator:recall}, in every state except {@code emergencyRecall}
 * itself: recall pre-empts everything else, including maintenance and
 * an ongoing movement, so no other state gates it -- but a car already
 * mid-recall, not yet arrived, offers nothing to anyone (not even this)
 * until it settles into {@code outOfService}, per {@code
 * docs/plan.html}'s "Elevator resource" specification.
 */
@Component
public class TriggerEmergencyRecallAffordanceContributor implements AffordanceContributor {

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        if (!context.principal().hasScope("elevator:recall")) {
            return List.of();
        }
        if ("emergencyRecall".equals(context.state().orElse(""))) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get();
        return List.of(new Affordance(
                "trigger-emergency-recall", "Trigger emergency recall", "POST", href,
                List.of(Field.hidden("type", "TriggerEmergencyRecall"))));
    }
}
