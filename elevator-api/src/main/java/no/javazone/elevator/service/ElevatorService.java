package no.javazone.elevator.service;

import java.time.Duration;
import java.time.Instant;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.model.Call;
import no.javazone.elevator.model.Direction;
import no.javazone.elevator.model.DoorState;
import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.model.ElevatorState;
import no.javazone.elevator.repository.CallRepository;
import no.javazone.elevator.repository.ElevatorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Elevator state derivation and call handling. State is computed on
 * read from elapsed wall-clock time rather than advanced by a
 * background scheduler -- see "Timing" in docs/architecture.md.
 */
@Service
public class ElevatorService {

    private final ElevatorRepository elevatorRepository;
    private final CallRepository callRepository;
    private final ElevatorProperties properties;

    public ElevatorService(
            ElevatorRepository elevatorRepository,
            CallRepository callRepository,
            ElevatorProperties properties) {
        this.elevatorRepository = elevatorRepository;
        this.callRepository = callRepository;
        this.properties = properties;
    }

    public Elevator getStatus(Long id) {
        Elevator elevator = findElevator(id);
        recomputeState(elevator);
        return elevator;
    }

    public Call call(Long elevatorId, Call request) {
        Elevator elevator = findElevator(elevatorId);
        recomputeState(elevator);

        if (request.getFloor() < 1 || request.getFloor() > properties.floors()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid floor");
        }
        if (elevator.getState() == ElevatorState.OUT_OF_SERVICE
                || elevator.getState() == ElevatorState.EMERGENCY_RECALL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevator is not in service");
        }

        Call call = new Call();
        call.setElevatorId(elevatorId);
        call.setFloor(request.getFloor());
        call.setDirection(request.getDirection());
        call.setCreatedAt(Instant.now());

        if (elevator.getState() == ElevatorState.IDLE) {
            if (call.getFloor() == elevator.getCurrentFloor()) {
                elevator.setState(ElevatorState.DOORS_OPEN);
                elevator.setDirection(Direction.NONE);
                elevator.setDoorState(DoorState.OPEN);
                elevator.setStateSince(Instant.now());
                call.setServedAt(Instant.now());
            } else {
                Direction direction = call.getFloor() > elevator.getCurrentFloor()
                        ? Direction.UP
                        : Direction.DOWN;
                elevator.setDirection(direction);
                elevator.setState(
                        direction == Direction.UP ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN);
                elevator.setTargetFloor(call.getFloor());
                elevator.setStateSince(Instant.now());
            }
            elevatorRepository.save(elevator);
        }
        // Else: elevator is busy: the call is left pending, to be picked up
        // by request-queue scheduling (see build-order slice 3).

        return callRepository.save(call);
    }

    public java.util.List<Call> listCalls(Long elevatorId) {
        findElevator(elevatorId);
        return callRepository.findByElevatorIdOrderByCreatedAtAsc(elevatorId);
    }

    private Elevator findElevator(Long id) {
        return elevatorRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * If the elevator is mid-travel, derive whether it has now arrived
     * based on elapsed time since stateSince. If arrived, finalize and
     * persist the arrival (mark the call served, open the doors). If
     * not yet arrived, mutate the in-memory (detached) entity's
     * currentFloor to reflect interim position for display, without
     * persisting it -- the persisted currentFloor remains the departure
     * floor until arrival.
     */
    private void recomputeState(Elevator elevator) {
        if (elevator.getState() != ElevatorState.MOVING_UP
                && elevator.getState() != ElevatorState.MOVING_DOWN) {
            return;
        }

        int departureFloor = elevator.getCurrentFloor();
        int targetFloor = elevator.getTargetFloor();
        int distance = Math.abs(targetFloor - departureFloor);
        long elapsedSeconds = Duration.between(elevator.getStateSince(), Instant.now()).getSeconds();
        long floorsTraveled = elapsedSeconds / properties.travelSecondsPerFloor();

        if (floorsTraveled >= distance) {
            elevator.setCurrentFloor(targetFloor);
            elevator.setState(ElevatorState.DOORS_OPEN);
            elevator.setDirection(Direction.NONE);
            elevator.setDoorState(DoorState.OPEN);
            elevator.setTargetFloor(null);
            elevator.setStateSince(Instant.now());
            elevatorRepository.save(elevator);

            callRepository.findByElevatorIdOrderByCreatedAtAsc(elevator.getId()).stream()
                    .filter(c -> c.getServedAt() == null && c.getFloor() == targetFloor)
                    .findFirst()
                    .ifPresent(c -> {
                        c.setServedAt(Instant.now());
                        callRepository.save(c);
                    });
        } else {
            int sign = targetFloor > departureFloor ? 1 : -1;
            elevator.setCurrentFloor(departureFloor + sign * (int) floorsTraveled);
        }
    }
}
