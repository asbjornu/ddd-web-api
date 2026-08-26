package no.javazone.elevator.shared.scheduler;

import java.time.Instant;
import java.util.List;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.Floor;
import no.javazone.elevator.shared.domain.MovementStarted;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Schedules {@link Elevator#arrive} at the instant a
 * {@link MovementStarted} event says the car will get there -- the
 * "timing change that unlocks everything" from
 * {@code docs/architecture.md}'s "Domain model" section: state is
 * derived forward, from a scheduled instant computed once at dispatch,
 * rather than backward from elapsed wall-clock time on every read.
 *
 * <p>Shared across every command that can dispatch the car (today,
 * {@code call-elevator} and {@code select-floor}), per {@code
 * docs/architecture.md}'s "Vertical slices" section -- neither slice
 * owns movement, both trigger it. Reacts to {@link MovementStarted} via
 * {@link EventListener} rather than being called directly, so it need
 * not depend on {@link DoorScheduler} even though arrival can itself
 * produce a {@code DoorsOpened} event -- see {@link CommandEffects}'s
 * Javadoc.
 */
@Component
public class MovementScheduler {

    private final TaskScheduler taskScheduler;
    private final ElevatorAggregateStore store;
    private final ElevatorViewUpdates updates;
    private final ElevatorViewProjection projection;
    private final ElevatorProperties properties;
    private final CommandEffects effects;

    public MovementScheduler(
            TaskScheduler movementTaskScheduler,
            ElevatorAggregateStore store,
            ElevatorViewUpdates updates,
            ElevatorViewProjection projection,
            ElevatorProperties properties,
            CommandEffects effects) {
        this.taskScheduler = movementTaskScheduler;
        this.store = store;
        this.updates = updates;
        this.projection = projection;
        this.properties = properties;
        this.effects = effects;
    }

    @EventListener
    public void onMovementStarted(MovementStarted event) {
        int distance = Math.abs(event.to().level() - event.from().level());
        Instant arrivalInstant = event.departedAt()
                .plusSeconds((long) distance * properties.travelSecondsPerFloor());
        taskScheduler.schedule(
                () -> handleArrival(event.elevatorId(), event.to()), arrivalInstant);
    }

    private void handleArrival(ElevatorId id, Floor destination) {
        Elevator elevator = store.find(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Elevator " + id.value() + " disappeared before it arrived"));
        List<DomainEvent> events = elevator.arrive(destination);
        store.save(elevator);
        effects.apply(elevator, events);
        updates.publish(id, projection.find(id).orElseThrow());
    }
}
