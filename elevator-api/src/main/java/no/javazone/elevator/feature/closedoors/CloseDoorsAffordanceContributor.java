package no.javazone.elevator.feature.closedoors;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code close-doors} whenever the doors are open -- including
 * while obstructed: the affordance reflects that the doors are open
 * and closing them is the ordinary next step, and the command itself is
 * what refuses with a 409 when obstruction (or overload) makes that
 * impossible right now. See this slice's commit message for why
 * omission isn't used for obstruction the way it is for state.
 */
@Component
public class CloseDoorsAffordanceContributor implements AffordanceContributor {

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
                "close-doors", "Close doors", "POST", href,
                List.of(Field.hidden("type", "CloseDoors"))));
    }
}
