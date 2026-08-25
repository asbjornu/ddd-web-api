package no.javazone.elevator.feature.viewstatus;

import java.util.Optional;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.ElevatorStateNames;
import org.springframework.stereotype.Component;

/**
 * The query side's only entry point into {@link ElevatorViewRepository}
 * -- queries never touch the write-side aggregate, per
 * {@code docs/architecture.md}'s "CQRS and domain events" section.
 *
 * <p>{@link #syncFrom} is this class's projection proper: called by a
 * command handler (after the aggregate is saved) or by
 * {@code shared.scheduler} (after an arrival), it folds the aggregate's
 * current state into this table -- still a direct copy rather than a
 * per-event-type {@code @EventListener}, since every event so far
 * changes the same handful of fields the same way. That may be worth
 * revisiting once an event exists whose effect on the view isn't "read
 * the aggregate again".
 */
@Component
public class ElevatorViewProjection {

    private final ElevatorViewRepository repository;

    public ElevatorViewProjection(ElevatorViewRepository repository) {
        this.repository = repository;
    }

    public Optional<ElevatorView> find(ElevatorId id) {
        return repository.findById(id.value()).map(ElevatorView::from);
    }

    public void syncFrom(Elevator elevator) {
        ElevatorViewEntity entity = repository.findById(elevator.id().value())
                .orElseThrow(() -> new IllegalStateException(
                        "No read-side row for elevator " + elevator.id().value()
                                + " -- it must be seeded, never created by a command"));
        entity.setCurrentFloor(elevator.currentFloor().level());
        entity.setState(ElevatorStateNames.of(elevator.state()));
        entity.setDirection(ElevatorStateNames.directionOf(elevator.state()));
        entity.setDoorPosition(elevator.doors().position().name().toLowerCase());
        entity.setObstructed(elevator.doors().obstructed());
        entity.setWeightKg(elevator.load().kilograms());
        entity.setCapacityKg(elevator.load().capacityKilograms());
        entity.setDestinationFloor(ElevatorStateNames.destinationOf(elevator.state()));
        repository.save(entity);
    }
}
