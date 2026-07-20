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

@Service
public class ElevatorService {

    private static final long DOOR_CLOSE_DURATION_SECONDS = 2;

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
            dispatchToFloor(elevator, call);
        } else if (elevator.getState() == ElevatorState.DOORS_OPEN
                && call.getFloor() == elevator.getCurrentFloor()) {
            call.setServedAt(Instant.now());
            elevator.setStateSince(Instant.now());
            elevatorRepository.save(elevator);
        }

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

    private void recomputeState(Elevator elevator) {
        long elapsedSeconds = Duration.between(elevator.getStateSince(), Instant.now()).getSeconds();

        if (elevator.getState() == ElevatorState.MOVING_UP
                || elevator.getState() == ElevatorState.MOVING_DOWN) {
            recomputeMovement(elevator, elapsedSeconds);
        } else if (elevator.getState() == ElevatorState.DOORS_OPEN) {
            if (elapsedSeconds >= properties.doorOpenTimeoutSeconds()) {
                elevator.setDoorState(DoorState.CLOSING);
                elevator.setState(ElevatorState.DOORS_CLOSING);
                elevator.setStateSince(Instant.now());
                elevatorRepository.save(elevator);
            }
        } else if (elevator.getState() == ElevatorState.DOORS_CLOSING) {
            if (elapsedSeconds >= DOOR_CLOSE_DURATION_SECONDS) {
                elevator.setDoorState(DoorState.CLOSED);
                elevator.setState(ElevatorState.IDLE);
                elevator.setStateSince(Instant.now());
                elevatorRepository.save(elevator);
                serveNextPendingCall(elevator);
            }
        }
    }

    private void recomputeMovement(Elevator elevator, long elapsedSeconds) {
        int departureFloor = elevator.getCurrentFloor();
        int targetFloor = elevator.getTargetFloor();
        int distance = Math.abs(targetFloor - departureFloor);
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

    private void dispatchToFloor(Elevator elevator, Call call) {
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

    private void serveNextPendingCall(Elevator elevator) {
        callRepository.findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(elevator.getId())
                .stream()
                .findFirst()
                .ifPresent(call -> {
                    elevator.setStateSince(Instant.now());
                    dispatchToFloor(elevator, call);
                });
    }
}
