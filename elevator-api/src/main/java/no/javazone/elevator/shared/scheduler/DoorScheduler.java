package no.javazone.elevator.shared.scheduler;

import java.time.Instant;
import java.util.List;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.DoorsClosingStarted;
import no.javazone.elevator.shared.domain.DoorsOpened;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.render.ElevatorStateJsonRenderer;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Schedules the two timers doors have always had, in the target
 * architecture as much as the CRUD service it replaces: open long
 * enough ({@code doorOpenTimeoutSeconds}), then close, unless
 * obstructed or overloaded; closing takes {@link #CLOSE_DURATION_SECONDS},
 * unless an obstruction re-opens them first. Reacts to
 * {@link DoorsOpened} and {@link DoorsClosingStarted} via
 * {@link EventListener} -- see {@link CommandEffects}'s Javadoc for why
 * this need not (and does not) depend on {@link MovementScheduler}, even
 * though finishing a close can dispatch the next pending call.
 *
 * <p>{@code CLOSE_DURATION_SECONDS} is the one timing constant kept
 * hard-coded, matching the old service's own -- it is short and purely
 * cosmetic (how long the doors take to physically close once
 * committed), not a value a rider or a test ever waits on the way they
 * wait on {@code travelSecondsPerFloor} or {@code doorOpenTimeoutSeconds}.
 */
@Component
public class DoorScheduler {

    private static final long CLOSE_DURATION_SECONDS = 2;

    private final TaskScheduler taskScheduler;
    private final ElevatorAggregateStore store;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer renderer;
    private final ElevatorProperties properties;
    private final CommandEffects effects;

    public DoorScheduler(
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
    public void onDoorsOpened(DoorsOpened event) {
        Instant at = event.occurredAt().plusSeconds(properties.doorOpenTimeoutSeconds());
        taskScheduler.schedule(() -> autoClose(event.elevatorId()), at);
    }

    @EventListener
    public void onDoorsClosingStarted(DoorsClosingStarted event) {
        Instant at = event.occurredAt().plusSeconds(CLOSE_DURATION_SECONDS);
        taskScheduler.schedule(() -> finishClosing(event.elevatorId()), at);
    }

    private void autoClose(ElevatorId id) {
        Elevator elevator = elevatorOrThrow(id);
        List<DomainEvent> events = elevator.autoCloseIfStillOpen();
        applyAndPublish(elevator, events);
    }

    private void finishClosing(ElevatorId id) {
        Elevator elevator = elevatorOrThrow(id);
        List<DomainEvent> events = elevator.finishClosingIfStillClosing();
        applyAndPublish(elevator, events);
    }

    private Elevator elevatorOrThrow(ElevatorId id) {
        return store.find(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Elevator " + id.value() + " disappeared before its door timer fired"));
    }

    private void applyAndPublish(Elevator elevator, List<DomainEvent> events) {
        if (events.isEmpty()) {
            // Nothing to do: the doors were already closed by an
            // explicit command, or an obstruction re-opened them first.
            return;
        }
        store.save(elevator);
        effects.apply(elevator, events);
        updates.publish(elevator.id(), renderer.render(EventRepresentations.of(elevator)));
    }
}
