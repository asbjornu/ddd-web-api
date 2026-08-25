package no.javazone.elevator.shared.scheduler;

import java.time.Instant;
import java.util.List;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.EmergencyRecallTriggered;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.render.ElevatorStateJsonRenderer;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Schedules {@link Elevator#completeEmergencyRecall} at the instant an
 * {@link EmergencyRecallTriggered} event says the car will reach the
 * recall floor -- the same "derive state forward from a scheduled
 * instant" timing {@link MovementScheduler} already uses for an
 * ordinary dispatch, applied to the one journey that pre-empts it.
 *
 * <p>Does nothing if {@code fromFloor} already equals {@code
 * recallFloor}: {@link Elevator#triggerEmergencyRecall} already settled
 * into {@code outOfService} synchronously in that case, and there is no
 * later instant to schedule anything at.
 */
@Component
public class RecallScheduler {

    private final TaskScheduler taskScheduler;
    private final ElevatorAggregateStore store;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer renderer;
    private final ElevatorProperties properties;
    private final CommandEffects effects;

    public RecallScheduler(
            TaskScheduler movementTaskScheduler,
            ElevatorAggregateStore store,
            ElevatorViewUpdates updates,
            ElevatorStateJsonRenderer renderer,
            ElevatorProperties properties,
            CommandEffects effects) {
        this.taskScheduler = movementTaskScheduler;
        this.store = store;
        this.updates = updates;
        this.renderer = renderer;
        this.properties = properties;
        this.effects = effects;
    }

    @EventListener
    public void onEmergencyRecallTriggered(EmergencyRecallTriggered event) {
        if (event.fromFloor().level() == event.recallFloor().level()) {
            return;
        }
        int distance = Math.abs(event.recallFloor().level() - event.fromFloor().level());
        Instant arrivalInstant = event.occurredAt()
                .plusSeconds((long) distance * properties.travelSecondsPerFloor());
        taskScheduler.schedule(() -> handleRecallArrival(event.elevatorId()), arrivalInstant);
    }

    private void handleRecallArrival(ElevatorId id) {
        Elevator elevator = store.find(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Elevator " + id.value() + " disappeared before its recall completed"));
        List<DomainEvent> events = elevator.completeEmergencyRecall();
        store.save(elevator);
        effects.apply(elevator, events);
        updates.publish(id, renderer.render(EventRepresentations.of(elevator)));
    }
}
