package no.javazone.elevator.feature.obstructdoors;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code obstruct-doors} only while the doors are closing --
 * there is nothing to obstruct otherwise, and this is the affordance
 * "obstruct-doors offered only while closing" names (see this slice's
 * roadmap entry).
 */
@Component
public class ObstructDoorsAffordanceContributor implements AffordanceContributor {

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        if (!"doorsClosing".equals(context.state().orElse(""))) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get();
        return List.of(new Affordance(
                "obstruct-doors", "Simulate obstruction", "POST", href,
                List.of(Field.hidden("type", "ObstructDoors"))));
    }
}
