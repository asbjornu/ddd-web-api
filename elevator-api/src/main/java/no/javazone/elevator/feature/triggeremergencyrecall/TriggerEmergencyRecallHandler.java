package no.javazone.elevator.feature.triggeremergencyrecall;

import java.util.List;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.Floor;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.scheduler.CommandEffects;
import org.springframework.stereotype.Component;

/**
 * {@code command -> handler -> aggregate -> events} for
 * trigger-emergency-recall. Which floor is "the" recall floor is
 * building configuration, not something the aggregate knows or the
 * caller supplies -- read once here, from {@link ElevatorProperties},
 * and passed into {@link Elevator#triggerEmergencyRecall} as an
 * ordinary {@link Floor} value, the same way every other command
 * passes its own floor arguments in.
 */
@Component
public class TriggerEmergencyRecallHandler {

    /** Thrown when the command names an elevator that does not exist. */
    public static class UnknownElevator extends RuntimeException {
        public UnknownElevator(ElevatorId id) {
            super("No elevator known by id " + id.value());
        }
    }

    private final ElevatorAggregateStore store;
    private final CommandEffects effects;
    private final ElevatorProperties properties;

    public TriggerEmergencyRecallHandler(
            ElevatorAggregateStore store, CommandEffects effects, ElevatorProperties properties) {
        this.store = store;
        this.effects = effects;
        this.properties = properties;
    }

    public List<DomainEvent> handle(TriggerEmergencyRecallCommand command) {
        Elevator elevator = store.find(command.elevatorId())
                .orElseThrow(() -> new UnknownElevator(command.elevatorId()));
        Floor recallFloor = new Floor(properties.recallFloor(), true);
        List<DomainEvent> events = elevator.triggerEmergencyRecall(recallFloor);
        store.save(elevator);
        effects.apply(elevator, events);
        return events;
    }
}
