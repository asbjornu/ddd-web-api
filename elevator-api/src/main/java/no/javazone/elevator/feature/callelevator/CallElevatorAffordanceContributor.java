package no.javazone.elevator.feature.callelevator;

import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.AffordanceContributor;
import no.javazone.elevator.shared.hypermedia.Field;
import org.springframework.stereotype.Component;

/**
 * Offers {@code call-elevator} for any elevator that is answering calls
 * at all -- absent only while {@code outOfService} or
 * {@code emergencyRecall}, the same rule
 * {@link no.javazone.elevator.shared.domain.Elevator#call} enforces.
 * Restating it here (rather than probing the aggregate with a command
 * that might be refused) is what lets a rider client show no call
 * button at all, never a disabled one -- see
 * {@code docs/architecture.md}'s "Affordances: hypermedia over the
 * aggregate" section.
 */
@Component
public class CallElevatorAffordanceContributor implements AffordanceContributor {

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
                "call-elevator",
                "Call elevator",
                "POST",
                href,
                List.of(
                        Field.hidden("type", "CallElevator"),
                        Field.text("floor", null),
                        Field.select("direction", null, List.of("up", "down")))));
    }
}
