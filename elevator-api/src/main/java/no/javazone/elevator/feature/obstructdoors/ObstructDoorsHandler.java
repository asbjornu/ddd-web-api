package no.javazone.elevator.feature.obstructdoors;

import java.util.List;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.scheduler.CommandEffects;
import org.springframework.stereotype.Component;

/**
 * {@code command -> handler -> aggregate -> events} for obstruct-doors.
 * {@link CommandRefused} propagates unchanged (not currently closing)
 * for the controller to translate into a 409.
 */
@Component
public class ObstructDoorsHandler {

    /** Thrown when the command names an elevator that does not exist. */
    public static class UnknownElevator extends RuntimeException {
        public UnknownElevator(ElevatorId id) {
            super("No elevator known by id " + id.value());
        }
    }

    private final ElevatorAggregateStore store;
    private final CommandEffects effects;

    public ObstructDoorsHandler(ElevatorAggregateStore store, CommandEffects effects) {
        this.store = store;
        this.effects = effects;
    }

    public List<DomainEvent> handle(ObstructDoorsCommand command) {
        Elevator elevator = store.find(command.elevatorId())
                .orElseThrow(() -> new UnknownElevator(command.elevatorId()));
        List<DomainEvent> events = elevator.obstructDoors();
        store.save(elevator);
        effects.apply(elevator, events);
        return events;
    }
}
