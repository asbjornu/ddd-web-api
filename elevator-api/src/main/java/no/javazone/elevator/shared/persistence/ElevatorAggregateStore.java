package no.javazone.elevator.shared.persistence;

import java.util.List;
import java.util.Optional;
import no.javazone.elevator.shared.domain.Direction;
import no.javazone.elevator.shared.domain.Doors;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.ElevatorState;
import no.javazone.elevator.shared.domain.Floor;
import no.javazone.elevator.shared.domain.LandingCall;
import no.javazone.elevator.shared.domain.Load;
import no.javazone.elevator.shared.domain.RequestQueue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write side's persistence adapter: loads a pure {@link Elevator}
 * for a command handler to call a command on, and saves the result back
 * -- a JPA entity confined to this package either side of the mapping,
 * never leaked as the aggregate itself. Shared across every command
 * slice, per {@code docs/architecture.md}'s "Vertical slices" section
 * ("Persistence adapters" are listed as deliberately shared, not
 * sliced).
 */
@Component
public class ElevatorAggregateStore {

    private final ElevatorAggregateJpaRepository elevators;
    private final LandingCallJpaRepository landingCalls;

    public ElevatorAggregateStore(
            ElevatorAggregateJpaRepository elevators, LandingCallJpaRepository landingCalls) {
        this.elevators = elevators;
        this.landingCalls = landingCalls;
    }

    public Optional<Elevator> find(ElevatorId id) {
        return elevators.findById(id.value()).map(entity -> toDomain(id, entity));
    }

    @Transactional
    public void save(Elevator elevator) {
        ElevatorAggregateEntity entity =
                elevators.findById(elevator.id().value()).orElseGet(ElevatorAggregateEntity::new);
        entity.setId(elevator.id().value());
        entity.setCurrentFloor(elevator.currentFloor().level());
        entity.setState(stateName(elevator.state()));
        entity.setObstructed(elevator.doors().obstructed());
        entity.setDoorPosition(elevator.doors().position().name().toLowerCase());
        entity.setWeightKg(elevator.load().kilograms());
        entity.setCapacityKg(elevator.load().capacityKilograms());
        elevators.save(entity);

        // Whole-queue replace rather than a diff: simple, and cheap at
        // this scale. Worth revisiting once a call can be served
        // (removed) as well as added, from slice 3 onward.
        landingCalls.deleteByElevatorId(elevator.id().value());
        for (LandingCall call : elevator.queue().pendingLandingCalls()) {
            landingCalls.save(new LandingCallEntity(
                    elevator.id().value(), call.floor().level(), call.direction().name()));
        }
    }

    private Elevator toDomain(ElevatorId id, ElevatorAggregateEntity entity) {
        List<LandingCall> pending = landingCalls.findByElevatorId(entity.getId()).stream()
                .map(row -> new LandingCall(
                        new Floor(row.getFloor()), Direction.valueOf(row.getDirection())))
                .toList();
        return Elevator.restore(
                id,
                new Floor(entity.getCurrentFloor()),
                stateFromName(entity.getState()),
                new Doors(
                        Doors.DoorPosition.valueOf(entity.getDoorPosition().toUpperCase()),
                        entity.isObstructed()),
                new Load(entity.getWeightKg(), entity.getCapacityKg()),
                RequestQueue.of(pending));
    }

    private String stateName(ElevatorState state) {
        return switch (state) {
            case ElevatorState.Idle s -> "idle";
            case ElevatorState.DoorsOpen s -> "doorsOpen";
            case ElevatorState.DoorsClosing s -> "doorsClosing";
            case ElevatorState.MovingUp s -> "movingUp";
            case ElevatorState.MovingDown s -> "movingDown";
            case ElevatorState.OutOfService s -> "outOfService";
            case ElevatorState.EmergencyRecall s -> "emergencyRecall";
        };
    }

    // MovingUp/MovingDown/EmergencyRecall are not reachable by any
    // command yet (they carry a destination floor this table has no
    // column for) -- restoring one is deliberately unsupported until
    // the slice that produces it also adds the column. idle,
    // doorsOpen, doorsClosing and outOfService carry no data of their
    // own, so name alone is enough to restore them.
    private ElevatorState stateFromName(String name) {
        return switch (name) {
            case "idle" -> new ElevatorState.Idle();
            case "doorsOpen" -> new ElevatorState.DoorsOpen();
            case "doorsClosing" -> new ElevatorState.DoorsClosing();
            case "outOfService" -> new ElevatorState.OutOfService();
            default -> throw new IllegalStateException(
                    "Cannot restore elevator state \"" + name + "\" yet");
        };
    }
}
