package no.javazone.elevator.feature.exitmaintenance;

import java.util.List;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.scheduler.CommandEffects;
import org.springframework.stereotype.Component;

/**
 * {@code command -> handler -> aggregate -> events} for exit-maintenance.
 * {@link CommandRefused} propagates unchanged (not in maintenance) for
 * the controller to translate into a 409.
 */
@Component
public class ExitMaintenanceHandler {

    /** Thrown when the command names an elevator that does not exist. */
    public static class UnknownElevator extends RuntimeException {
        public UnknownElevator(ElevatorId id) {
            super("No elevator known by id " + id.value());
        }
    }

    private final ElevatorAggregateStore store;
    private final CommandEffects effects;

    public ExitMaintenanceHandler(ElevatorAggregateStore store, CommandEffects effects) {
        this.store = store;
        this.effects = effects;
    }

    public List<DomainEvent> handle(ExitMaintenanceCommand command) {
        Elevator elevator = store.find(command.elevatorId())
                .orElseThrow(() -> new UnknownElevator(command.elevatorId()));
        List<DomainEvent> events = elevator.exitMaintenance();
        store.save(elevator);
        effects.apply(elevator, events);
        return events;
    }
}
