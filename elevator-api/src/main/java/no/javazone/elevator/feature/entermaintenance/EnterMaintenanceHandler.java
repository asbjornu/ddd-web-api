package no.javazone.elevator.feature.entermaintenance;

import java.util.List;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.scheduler.CommandEffects;
import org.springframework.stereotype.Component;

/**
 * {@code command -> handler -> aggregate -> events} for enter-maintenance.
 * {@link CommandRefused} propagates unchanged (mid-recall) for the
 * controller to translate into a 409.
 */
@Component
public class EnterMaintenanceHandler {

    /** Thrown when the command names an elevator that does not exist. */
    public static class UnknownElevator extends RuntimeException {
        public UnknownElevator(ElevatorId id) {
            super("No elevator known by id " + id.value());
        }
    }

    private final ElevatorAggregateStore store;
    private final CommandEffects effects;

    public EnterMaintenanceHandler(ElevatorAggregateStore store, CommandEffects effects) {
        this.store = store;
        this.effects = effects;
    }

    public List<DomainEvent> handle(EnterMaintenanceCommand command) {
        Elevator elevator = store.find(command.elevatorId())
                .orElseThrow(() -> new UnknownElevator(command.elevatorId()));
        List<DomainEvent> events = elevator.enterMaintenance();
        store.save(elevator);
        effects.apply(elevator, events);
        return events;
    }
}
