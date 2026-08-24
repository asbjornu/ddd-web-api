package no.javazone.elevator.shared.scheduler;

import java.time.Instant;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.ElevatorStateNames;
import no.javazone.elevator.shared.domain.Floor;
import no.javazone.elevator.shared.domain.MovementStarted;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.render.ElevatorStateJsonRenderer;
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
 * owns movement, both trigger it.
 */
@Component
public class MovementScheduler {

    private final TaskScheduler taskScheduler;
    private final ElevatorAggregateStore store;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer renderer;
    private final ElevatorProperties properties;

    public MovementScheduler(
            TaskScheduler movementTaskScheduler,
            ElevatorAggregateStore store,
            ElevatorViewProjection projection,
            ElevatorViewUpdates updates,
            ElevatorStateJsonRenderer renderer,
            ElevatorProperties properties) {
        this.taskScheduler = movementTaskScheduler;
        this.store = store;
        this.projection = projection;
        this.updates = updates;
        this.renderer = renderer;
        this.properties = properties;
    }

    /** Called by a command handler right after it saves the {@link MovementStarted} event. */
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
        elevator.arrive(destination);
        store.save(elevator);
        projection.syncFrom(elevator);
        updates.publish(id, renderer.render(eventRepresentation(elevator)));
    }

    private Representation eventRepresentation(Elevator elevator) {
        return Representation.builder("Elevator")
                .property("currentFloor", elevator.currentFloor().level())
                .property("state", ElevatorStateNames.of(elevator.state()))
                .property("direction", ElevatorStateNames.directionOf(elevator.state()))
                .property("doorPosition", elevator.doors().position().name().toLowerCase())
                .property("obstructed", elevator.doors().obstructed())
                .property("weightKg", elevator.load().kilograms())
                .property("capacityKg", elevator.load().capacityKilograms())
                .build();
    }
}
