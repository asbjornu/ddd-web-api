package no.javazone.elevator.shared.scheduler;

import java.time.Instant;
import java.util.List;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.autoclosedoors.AutoCloseDoorsCommand;
import no.javazone.elevator.feature.autoclosedoors.AutoCloseDoorsHandler;
import no.javazone.elevator.feature.finishclosingdoors.FinishClosingDoorsCommand;
import no.javazone.elevator.feature.finishclosingdoors.FinishClosingDoorsHandler;
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
 * <p>This class is the sensor {@link AutoCloseDoorsCommand} and
 * {@link FinishClosingDoorsCommand} stand in for: it decides *when*
 * each timer has elapsed (from the schedule), constructs the command,
 * and hands it to the matching handler exactly as a controller would a
 * rider's -- the difference is that nothing hands this scheduler its
 * own commands from outside; it originates them itself, on the clock.
 * Still holds {@link ElevatorAggregateStore} directly (unlike the
 * handlers' other callers) purely to re-read the aggregate for {@link
 * EventRepresentations#of}'s own JSON rendering afterward -- the
 * handlers' own return value is only the events, same as every other
 * handler in this codebase.
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
    private final AutoCloseDoorsHandler autoCloseHandler;
    private final FinishClosingDoorsHandler finishClosingHandler;
    private final ElevatorAggregateStore store;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer renderer;
    private final ElevatorProperties properties;

    public DoorScheduler(
            TaskScheduler movementTaskScheduler,
            AutoCloseDoorsHandler autoCloseHandler,
            FinishClosingDoorsHandler finishClosingHandler,
            ElevatorAggregateStore store,
            ElevatorViewUpdates updates,
            ElevatorStateJsonRenderer renderer,
            ElevatorProperties properties) {
        this.taskScheduler = movementTaskScheduler;
        this.autoCloseHandler = autoCloseHandler;
        this.finishClosingHandler = finishClosingHandler;
        this.store = store;
        this.updates = updates;
        this.renderer = renderer;
        this.properties = properties;
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
        List<DomainEvent> events = autoCloseHandler.handle(new AutoCloseDoorsCommand(id));
        publishIfChanged(id, events);
    }

    private void finishClosing(ElevatorId id) {
        List<DomainEvent> events = finishClosingHandler.handle(new FinishClosingDoorsCommand(id));
        publishIfChanged(id, events);
    }

    private void publishIfChanged(ElevatorId id, List<DomainEvent> events) {
        if (events.isEmpty()) {
            // Nothing to do: the doors were already closed by an
            // explicit command, or an obstruction re-opened them first.
            return;
        }
        Elevator elevator = store.find(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Elevator " + id.value() + " disappeared right after its own door-timer command"));
        updates.publish(id, renderer.render(EventRepresentations.of(elevator, properties)));
    }
}
