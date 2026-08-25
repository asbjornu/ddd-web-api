package no.javazone.elevator.feature.exitmaintenance;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code exit-maintenance} only to a caller holding {@code
 * elevator:maintenance}, and only while the car actually is {@code
 * outOfService} -- see {@code
 * no.javazone.elevator.feature.entermaintenance.EnterMaintenanceAffordanceContributor}
 * for the mirror-image rule.
 */
@Component
public class ExitMaintenanceAffordanceContributor implements AffordanceContributor {

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        if (!context.principal().hasScope("elevator:maintenance")) {
            return List.of();
        }
        if (!"outOfService".equals(context.state().orElse(""))) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get();
        return List.of(new Affordance(
                "exit-maintenance", "Exit maintenance", "POST", href,
                List.of(Field.hidden("type", "ExitMaintenance"))));
    }
}
