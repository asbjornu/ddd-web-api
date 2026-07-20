package no.javazone.elevator.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.model.Call;
import no.javazone.elevator.model.CarCall;
import no.javazone.elevator.model.Direction;
import no.javazone.elevator.model.DoorState;
import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.model.ElevatorState;
import no.javazone.elevator.repository.CallRepository;
import no.javazone.elevator.repository.CarCallRepository;
import no.javazone.elevator.repository.ElevatorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ElevatorService {

    private static final long DOOR_CLOSE_DURATION_SECONDS = 2;

    private final ElevatorRepository elevatorRepository;
    private final CallRepository callRepository;
    private final CarCallRepository carCallRepository;
    private final ElevatorProperties properties;

    public ElevatorService(
            ElevatorRepository elevatorRepository,
            CallRepository callRepository,
            CarCallRepository carCallRepository,
            ElevatorProperties properties) {
        this.elevatorRepository = elevatorRepository;
        this.callRepository = callRepository;
        this.carCallRepository = carCallRepository;
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
            dispatchToFloor(elevator, call.getFloor());
        } else if (elevator.getState() == ElevatorState.DOORS_OPEN
                && call.getFloor() == elevator.getCurrentFloor()) {
            call.setServedAt(Instant.now());
            elevator.setStateSince(Instant.now());
            elevatorRepository.save(elevator);
        }

        return callRepository.save(call);
    }

    public CarCall carCall(Long elevatorId, CarCall request) {
        Elevator elevator = findElevator(elevatorId);
        recomputeState(elevator);

        if (request.getFloor() < 1 || request.getFloor() > properties.floors()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid floor");
        }
        if (elevator.getState() == ElevatorState.OUT_OF_SERVICE
                || elevator.getState() == ElevatorState.EMERGENCY_RECALL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevator is not in service");
        }
        if (request.getFloor() == elevator.getCurrentFloor()
                && elevator.getState() == ElevatorState.DOORS_OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already at this floor");
        }

        CarCall carCall = new CarCall();
        carCall.setElevatorId(elevatorId);
        carCall.setFloor(request.getFloor());
        carCall.setCreatedAt(Instant.now());

        if (elevator.getState() == ElevatorState.IDLE) {
            dispatchToFloor(elevator, carCall.getFloor());
        }

        return carCallRepository.save(carCall);
    }

    public java.util.List<Call> listCalls(Long elevatorId) {
        findElevator(elevatorId);
        return callRepository.findByElevatorIdOrderByCreatedAtAsc(elevatorId);
    }

    public java.util.List<CarCall> listCarCalls(Long elevatorId) {
        findElevator(elevatorId);
        return carCallRepository.findByElevatorIdOrderByCreatedAtAsc(elevatorId);
    }

    private Elevator findElevator(Long id) {
        return elevatorRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // ──────────────────────────────────────────────
    //  State recomputation (called on every read)
    // ──────────────────────────────────────────────

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
            serveFloor(elevator, targetFloor);
            return;
        }

        int sign = targetFloor > departureFloor ? 1 : -1;
        int interimFloor = departureFloor + sign * (int) floorsTraveled;

        // Check if we've reached or passed any pending floor in the current direction
        Integer extraStop = findPendingFloorOnPath(elevator, departureFloor,
                interimFloor, sign);
        if (extraStop != null) {
            serveFloor(elevator, extraStop);
            return;
        }

        elevator.setCurrentFloor(interimFloor);
    }

    // ──────────────────────────────────────────────
    //  Direction-committed dispatch
    // ──────────────────────────────────────────────

    private void dispatchToFloor(Elevator elevator, int floor) {
        if (floor == elevator.getCurrentFloor()) {
            serveFloor(elevator, floor);
        } else {
            Direction direction = floor > elevator.getCurrentFloor()
                    ? Direction.UP : Direction.DOWN;
            elevator.setDirection(direction);
            elevator.setState(direction == Direction.UP
                    ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN);
            elevator.setTargetFloor(floor);
            elevator.setStateSince(Instant.now());
            elevatorRepository.save(elevator);
        }
    }

    private void serveNextPendingCall(Elevator elevator) {
        Set<Integer> pendingFloors = getPendingFloors(elevator.getId());
        if (pendingFloors.isEmpty()) return;

        int currentFloor = elevator.getCurrentFloor();
        Direction direction = elevator.getDirection();
        Integer nextFloor = null;

        if (direction == Direction.UP) {
            nextFloor = pendingFloors.stream()
                    .filter(f -> f > currentFloor)
                    .min(Integer::compareTo)
                    .orElse(null);
            if (nextFloor == null) {
                nextFloor = pendingFloors.stream()
                        .filter(f -> f < currentFloor)
                        .max(Integer::compareTo)
                        .orElse(null);
                if (nextFloor != null) elevator.setDirection(Direction.DOWN);
            }
        } else if (direction == Direction.DOWN) {
            nextFloor = pendingFloors.stream()
                    .filter(f -> f < currentFloor)
                    .max(Integer::compareTo)
                    .orElse(null);
            if (nextFloor == null) {
                nextFloor = pendingFloors.stream()
                        .filter(f -> f > currentFloor)
                        .min(Integer::compareTo)
                        .orElse(null);
                if (nextFloor != null) elevator.setDirection(Direction.UP);
            }
        } else {
            nextFloor = pendingFloors.stream()
                    .min(Comparator.comparingInt(f -> Math.abs(f - currentFloor)))
                    .orElse(null);
            if (nextFloor != null) {
                elevator.setDirection(nextFloor > currentFloor
                        ? Direction.UP : Direction.DOWN);
            }
        }

        if (nextFloor != null) {
            elevator.setTargetFloor(nextFloor);
            elevator.setState(nextFloor > currentFloor
                    ? ElevatorState.MOVING_UP : ElevatorState.MOVING_DOWN);
            elevator.setStateSince(Instant.now());
            elevatorRepository.save(elevator);
        }
    }

    // ──────────────────────────────────────────────
    //  Floor arrival
    // ──────────────────────────────────────────────

    private void serveFloor(Elevator elevator, int floor) {
        elevator.setCurrentFloor(floor);
        elevator.setState(ElevatorState.DOORS_OPEN);
        elevator.setDirection(Direction.NONE);
        elevator.setDoorState(DoorState.OPEN);
        elevator.setTargetFloor(null);
        elevator.setStateSince(Instant.now());
        elevatorRepository.save(elevator);

        callRepository.findByElevatorIdAndServedAtIsNullAndFloor(
                elevator.getId(), floor).forEach(c -> {
            c.setServedAt(Instant.now());
            callRepository.save(c);
        });
        carCallRepository.findByElevatorIdAndServedAtIsNullAndFloor(
                elevator.getId(), floor).forEach(cc -> {
            cc.setServedAt(Instant.now());
            carCallRepository.save(cc);
        });
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    private Set<Integer> getPendingFloors(Long elevatorId) {
        Set<Integer> floors = new HashSet<>();
        callRepository.findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(elevatorId)
                .forEach(c -> floors.add(c.getFloor()));
        carCallRepository.findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(elevatorId)
                .forEach(cc -> floors.add(cc.getFloor()));
        return floors;
    }

    /**
     * Returns a pending floor that we've reached or passed while traveling
     * from departureFloor to interimFloor in the given direction (sign),
     * excluding the current target (which will be handled on normal arrival).
     */
    private Integer findPendingFloorOnPath(Elevator elevator, int departureFloor,
            int interimFloor, int sign) {
        Set<Integer> pending = getPendingFloors(elevator.getId());
        int targetFloor = elevator.getTargetFloor();

        if (sign > 0) {
            return pending.stream()
                    .filter(f -> f > departureFloor && f <= interimFloor && f != targetFloor)
                    .max(Integer::compareTo)
                    .orElse(null);
        } else {
            return pending.stream()
                    .filter(f -> f < departureFloor && f >= interimFloor && f != targetFloor)
                    .min(Integer::compareTo)
                    .orElse(null);
        }
    }
}
