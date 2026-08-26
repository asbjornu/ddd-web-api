package no.javazone.elevator.feature.selectfloor;

import java.util.List;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import no.javazone.elevator.shared.hypermedia.FloorOptions;
import org.springframework.stereotype.Component;

/**
 * Offers {@code select-floor} for any elevator that is in service and
 * not overloaded -- absent while {@code outOfService} or
 * {@code emergencyRecall}, or while the car is overloaded, the same
 * rules {@link no.javazone.elevator.shared.domain.Elevator#selectFloor}
 * enforces. "select-floor absent when overloaded -- no 409 needed" is
 * the test named in this slice's (Overload) roadmap entry: a client
 * that never learns the car is overloaded also never has a reason to
 * attempt the command in the first place.
 */
@Component
public class SelectFloorAffordanceContributor implements AffordanceContributor {

    private static final List<String> UNAVAILABLE_STATES = List.of("outOfService", "emergencyRecall");

    private final ElevatorProperties properties;

    public SelectFloorAffordanceContributor(ElevatorProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Affordance> contribute(AffordanceContext context) {
        if (context.elevatorSegment().isEmpty()) {
            return List.of();
        }
        String state = context.state().orElse("");
        if (UNAVAILABLE_STATES.contains(state) || context.overloaded().orElse(false)) {
            return List.of();
        }
        String href = "/elevators/" + context.elevatorSegment().get();
        return List.of(new Affordance(
                "select-floor",
                "Select a floor",
                "POST",
                href,
                List.of(
                        Field.hidden("type", "SelectFloor"),
                        Field.select("floor", null, FloorOptions.upTo(properties.floors())))));
    }
}
