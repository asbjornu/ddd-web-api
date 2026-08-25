package no.javazone.elevator.shared.persistence;

import java.util.List;
import java.util.Optional;
import no.javazone.elevator.shared.domain.CarCall;
import no.javazone.elevator.shared.domain.Direction;
import no.javazone.elevator.shared.domain.Doors;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.ElevatorStateNames;
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
    private final CarCallJpaRepository carCalls;

    public ElevatorAggregateStore(
            ElevatorAggregateJpaRepository elevators,
            LandingCallJpaRepository landingCalls,
            CarCallJpaRepository carCalls) {
        this.elevators = elevators;
        this.landingCalls = landingCalls;
        this.carCalls = carCalls;
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
        entity.setState(ElevatorStateNames.of(elevator.state()));
        entity.setDestinationFloor(ElevatorStateNames.destinationOf(elevator.state()));
        entity.setObstructed(elevator.doors().obstructed());
        entity.setDoorPosition(elevator.doors().position().name().toLowerCase());
        entity.setWeightKg(elevator.load().kilograms());
        entity.setCapacityKg(elevator.load().capacityKilograms());
        elevators.save(entity);

        // Whole-queue replace rather than a diff: simple, and cheap at
        // this scale.
        landingCalls.deleteByElevatorId(elevator.id().value());
        for (LandingCall call : elevator.queue().pendingLandingCalls()) {
            landingCalls.save(new LandingCallEntity(
                    elevator.id().value(), call.floor().level(), call.direction().name()));
        }
        carCalls.deleteByElevatorId(elevator.id().value());
        for (CarCall call : elevator.queue().pendingCarCalls()) {
            carCalls.save(new CarCallEntity(elevator.id().value(), call.floor().level()));
        }
    }

    private Elevator toDomain(ElevatorId id, ElevatorAggregateEntity entity) {
        List<LandingCall> pendingLanding = landingCalls.findByElevatorId(entity.getId()).stream()
                .map(row -> new LandingCall(
                        new Floor(row.getFloor()), Direction.valueOf(row.getDirection())))
                .toList();
        List<CarCall> pendingCar = carCalls.findByElevatorId(entity.getId()).stream()
                .map(row -> new CarCall(new Floor(row.getFloor())))
                .toList();
        Integer destinationFloor = entity.getDestinationFloor();
        Floor destination = destinationFloor == null ? null : new Floor(destinationFloor);
        return Elevator.restore(
                id,
                new Floor(entity.getCurrentFloor()),
                ElevatorStateNames.fromName(entity.getState(), destination),
                new Doors(
                        Doors.DoorPosition.valueOf(entity.getDoorPosition().toUpperCase()),
                        entity.isObstructed()),
                new Load(entity.getWeightKg(), entity.getCapacityKg()),
                RequestQueue.of(pendingLanding, pendingCar));
    }
}
