package no.javazone.elevator.feature.selectfloor;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code select-floor} for any elevator that is in service --
 * absent while {@code outOfService} or {@code emergencyRecall}, the
 * same rule {@link no.javazone.elevator.shared.domain.Elevator#selectFloor}
 * enforces for those two states.
 *
 * <p>Overload is deliberately not checked here: whether the car is
 * overloaded lives on the aggregate's {@code Load}, which the read
 * model does not expose yet (that arrives with slice 5, Overload).
 * Until then, an overloaded car still offers the affordance and refuses
 * the command with a 409 -- an honest gap, not a bug; see this slice's
 * commit message.
 */
@Component
public class SelectFloorAffordanceContributor implements AffordanceContributor {

    private static final List<String> UNAVAILABLE_STATES = List.of("outOfService", "emergencyRecall");

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
                "select-floor",
                "Select a floor",
                "POST",
                href,
                List.of(Field.hidden("type", "SelectFloor"), Field.text("floor", null))));
    }
}
