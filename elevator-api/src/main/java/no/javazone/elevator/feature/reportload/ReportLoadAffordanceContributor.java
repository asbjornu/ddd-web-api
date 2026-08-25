package no.javazone.elevator.feature.reportload;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code report-load} only while the doors are open, matching
 * the physical setup this simulates -- a rider's weight only changes
 * the car's load while boarding or alighting.
 */
@Component
public class ReportLoadAffordanceContributor implements AffordanceContributor {

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        if (!"doorsOpen".equals(context.state().orElse(""))) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get();
        return List.of(new Affordance(
                "report-load", "Report load", "POST", href,
                List.of(Field.hidden("type", "ReportLoad"), Field.text("weightKg", null))));
    }
}
