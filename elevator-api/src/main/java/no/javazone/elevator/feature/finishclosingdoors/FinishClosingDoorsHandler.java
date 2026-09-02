package no.javazone.elevator.feature.finishclosingdoors;

import java.util.List;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.scheduler.CommandEffects;
import org.springframework.stereotype.Component;

/**
 * {@code command -> handler -> aggregate -> events}, same shape as
 * every rider-facing slice even though this command has no rider, no
 * controller, and no affordance -- see {@link FinishClosingDoorsCommand}'s
 * own Javadoc for why. {@code DoorScheduler} is this command's only
 * caller and is responsible for publishing the SSE update afterward:
 * unlike a rider command, there is no controller response to fold that
 * into.
 */
@Component
public class FinishClosingDoorsHandler {

    /** Thrown when the command names an elevator that does not exist. */
    public static class UnknownElevator extends RuntimeException {
        public UnknownElevator(ElevatorId id) {
            super("No elevator known by id " + id.value());
        }
    }

    private final ElevatorAggregateStore store;
    private final CommandEffects effects;

    public FinishClosingDoorsHandler(ElevatorAggregateStore store, CommandEffects effects) {
        this.store = store;
        this.effects = effects;
    }

    /**
     * Returns whatever {@link Elevator#finishClosingIfStillClosing}
     * returned -- empty if an obstruction already re-opened the doors
     * in the meantime, in which case there is nothing to save, publish,
     * or otherwise act on.
     */
    public List<DomainEvent> handle(FinishClosingDoorsCommand command) {
        Elevator elevator = store.find(command.elevatorId())
                .orElseThrow(() -> new UnknownElevator(command.elevatorId()));
        List<DomainEvent> events = elevator.finishClosingIfStillClosing();
        if (events.isEmpty()) {
            return events;
        }
        store.save(elevator);
        effects.apply(elevator, events);
        return events;
    }
}
