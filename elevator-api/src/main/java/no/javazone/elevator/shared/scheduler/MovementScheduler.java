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
 * Schedules {@link Elevator#passFloor} once per floor along the route a
 * {@link MovementStarted} event describes -- the "timing change that
 * unlocks everything" from {@code docs/architecture.md}'s "Domain
 * model" section: state is derived forward, from scheduled instants
 * computed once at dispatch, rather than backward from elapsed
 * wall-clock time on every read. One scheduled callback per floor,
 * not one for the whole trip, is what lets {@code currentFloor} (and
 * the {@link no.javazone.elevator.shared.domain.FloorPassed} event
 * pushed for it) advance while the car is still travelling, instead of
 * jumping straight to the destination the instant the trip finishes.
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
        int sign = event.to().level() > event.from().level() ? 1 : -1;
        for (int floorsTravelled = 1; floorsTravelled <= distance; floorsTravelled++) {
            Floor floor = new Floor(event.from().level() + sign * floorsTravelled);
            Instant at = event.departedAt()
                    .plusSeconds((long) floorsTravelled * properties.travelSecondsPerFloor());
            taskScheduler.schedule(() -> handlePassFloor(event.elevatorId(), floor), at);
        }
    }

    private void handlePassFloor(ElevatorId id, Floor floor) {
        Elevator elevator = store.find(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Elevator " + id.value() + " disappeared before it reached floor "
                                + floor.level()));
        List<DomainEvent> events = elevator.passFloor(floor);
        if (events.isEmpty()) {
            // The car left its moving state some other way before this
            // scheduled floor was reached (an emergency recall, most
            // plausibly) -- see Elevator#passFloor's own Javadoc. Every
            // other already-scheduled callback for this same trip will
            // find the same thing true and no-op the same way.
            return;
        }
        store.save(elevator);
        effects.apply(elevator, events);
        updates.publish(id, projection.find(id).orElseThrow());
    }
}
