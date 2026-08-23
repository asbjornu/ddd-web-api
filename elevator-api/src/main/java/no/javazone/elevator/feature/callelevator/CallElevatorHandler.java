package no.javazone.elevator.feature.callelevator;

import java.util.List;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import org.springframework.stereotype.Component;

/**
 * {@code command -> handler -> aggregate -> events}, all inside this
 * slice -- see {@code docs/architecture.md}'s "CQRS and domain events"
 * section. The aggregate is the only thing that may refuse
 * ({@link CommandRefused} propagates unchanged, for the controller to
 * translate into a 409 Problem); events are its only other output, and
 * nothing here returns a value derived from them -- the caller re-reads
 * the resource if it wants to see the result.
 */
@Component
public class CallElevatorHandler {

    /** Thrown when the command names an elevator that does not exist. */
    public static class UnknownElevator extends RuntimeException {
        public UnknownElevator(ElevatorId id) {
            super("No elevator known by id " + id.value());
        }
    }

    private final ElevatorAggregateStore store;

    public CallElevatorHandler(ElevatorAggregateStore store) {
        this.store = store;
    }

    public List<DomainEvent> handle(CallElevatorCommand command) {
        Elevator elevator = store.find(command.elevatorId())
                .orElseThrow(() -> new UnknownElevator(command.elevatorId()));
        List<DomainEvent> events = elevator.call(command.floor(), command.direction());
        store.save(elevator);
        return events;
    }
}
