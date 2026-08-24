package no.javazone.elevator.shared.scheduler;

import java.util.List;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.MovementStarted;
import org.springframework.stereotype.Component;

/**
 * What every command handler does after a successful
 * {@code aggregate.command(...)} call: fold the result into the read
 * model, and schedule an arrival if the car just dispatched. Shared so
 * {@code feature.callelevator} and {@code feature.selectfloor} (and
 * whichever slice needs it next) do not each reimplement it.
 */
@Component
public class CommandEffects {

    private final ElevatorViewProjection projection;
    private final MovementScheduler movementScheduler;

    public CommandEffects(ElevatorViewProjection projection, MovementScheduler movementScheduler) {
        this.projection = projection;
        this.movementScheduler = movementScheduler;
    }

    public void apply(Elevator elevator, List<DomainEvent> events) {
        projection.syncFrom(elevator);
        for (DomainEvent event : events) {
            if (event instanceof MovementStarted started) {
                movementScheduler.onMovementStarted(started);
            }
        }
    }
}
