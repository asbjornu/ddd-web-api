package no.javazone.elevator.feature.selectfloor;

import java.util.List;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.scheduler.CommandEffects;
import org.springframework.stereotype.Component;

/**
 * {@code command -> handler -> aggregate -> events} for select-floor --
 * see {@link no.javazone.elevator.feature.callelevator.CallElevatorHandler}
 * for the identical shape slice 2 already established.
 */
@Component
public class SelectFloorHandler {

    /** Thrown when the command names an elevator that does not exist. */
    public static class UnknownElevator extends RuntimeException {
        public UnknownElevator(ElevatorId id) {
            super("No elevator known by id " + id.value());
        }
    }

    private final ElevatorAggregateStore store;
    private final CommandEffects effects;

    public SelectFloorHandler(ElevatorAggregateStore store, CommandEffects effects) {
        this.store = store;
        this.effects = effects;
    }

    public List<DomainEvent> handle(SelectFloorCommand command) {
        Elevator elevator = store.find(command.elevatorId())
                .orElseThrow(() -> new UnknownElevator(command.elevatorId()));
        List<DomainEvent> events = elevator.selectFloor(command.floor());
        store.save(elevator);
        effects.apply(elevator, events);
        return events;
    }
}
