package no.javazone.elevator.feature.clearobstruction;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/** Offers {@code clear-obstruction} only while the doors are obstructed. */
@Component
public class ClearObstructionAffordanceContributor implements AffordanceContributor {

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        if (!context.obstructed().orElse(false)) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get();
        return List.of(new Affordance(
                "clear-obstruction", "Clear obstruction", "POST", href,
                List.of(Field.hidden("type", "ClearObstruction"))));
    }
}
