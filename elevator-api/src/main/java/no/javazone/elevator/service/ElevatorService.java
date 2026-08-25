package no.javazone.elevator.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

    // call(Long, Call) was removed in slice 2: landing calls now go
    // through feature.callelevator.CallElevatorHandler against the new
    // aggregate instead. listCalls/clearPendingCalls stay here, read by
    // maintenance/recall/the movement scheduler until their own slices
    // migrate them too.

    public CarCall carCall(Long elevatorId, CarCall carCall) {
        Elevator elevator = findElevator(elevatorId);
        recomputeState(elevator);

        if (carCall.getFloor() < 1 || carCall.getFloor() > properties.floors()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid floor");
        }
        if (elevator.getState() == ElevatorState.OUT_OF_SERVICE
                || elevator.getState() == ElevatorState.EMERGENCY_RECALL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevator is not in service");
        }
        if (carCall.getFloor() == elevator.getCurrentFloor()
                && elevator.getState() == ElevatorState.DOORS_OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already at this floor");
        }
        if (isOverloaded(elevator)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Overload detected");
        }

        carCall.setElevatorId(elevatorId);
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

    public Elevator openDoors(Long id) {
        Elevator elevator = findElevator(id);
        recomputeState(elevator);

        if (elevator.getState() == ElevatorState.MOVING_UP
                || elevator.getState() == ElevatorState.MOVING_DOWN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot open doors while moving");
        }
        if (elevator.getState() == ElevatorState.OUT_OF_SERVICE
                || elevator.getState() == ElevatorState.EMERGENCY_RECALL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Elevator is not in service");
        }

        elevator.setDoorState(DoorState.OPEN);
        elevator.setState(ElevatorState.DOORS_OPEN);
        elevator.setStateSince(Instant.now());
        return elevatorRepository.save(elevator);
    }

    public Elevator closeDoors(Long id) {
        Elevator elevator = findElevator(id);
        recomputeState(elevator);

        if (elevator.getState() != ElevatorState.DOORS_OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doors are not open");
        }
        if (elevator.isObstructed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Obstruction detected");
        }
        if (isOverloaded(elevator)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Overload detected");
        }

        elevator.setDoorState(DoorState.CLOSING);
        elevator.setState(ElevatorState.DOORS_CLOSING);
        elevator.setStateSince(Instant.now());
        return elevatorRepository.save(elevator);
    }

    public Elevator setObstruction(Long id, boolean obstructed) {
        Elevator elevator = findElevator(id);
        elevator.setObstructed(obstructed);
        if (obstructed) {
            recomputeState(elevator);
        }
        return elevatorRepository.save(elevator);
    }

    public Elevator setWeight(Long id, int weightKg) {
        Elevator elevator = findElevator(id);
        recomputeState(elevator);
        if (elevator.getState() != ElevatorState.DOORS_OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Doors must be open to change weight");
        }
        elevator.setCurrentWeightKg(weightKg);
        if (isOverloaded(elevator)) {
            clearPendingCarCalls(elevator);
            if (elevator.getState() == ElevatorState.DOORS_OPEN) {
                elevator.setStateSince(Instant.now());
            }
            recomputeState(elevator);
        }
        return elevatorRepository.save(elevator);
    }

    public Elevator enterMaintenance(Long id) {
        Elevator elevator = findElevator(id);
        recomputeState(elevator);
        if (elevator.getState() == ElevatorState.OUT_OF_SERVICE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already in maintenance");
        }
        if (elevator.getState() == ElevatorState.EMERGENCY_RECALL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot enter maintenance during emergency recall");
        }

        clearPendingCalls(elevator);
        clearPendingCarCalls(elevator);

        elevator.setState(ElevatorState.OUT_OF_SERVICE);
        elevator.setDirection(Direction.NONE);
        elevator.setDoorState(DoorState.CLOSED);
        elevator.setTargetFloor(null);
        elevator.setStateSince(Instant.now());
        return elevatorRepository.save(elevator);
    }

    public Elevator exitMaintenance(Long id) {
        Elevator elevator = findElevator(id);
        recomputeState(elevator);
        if (elevator.getState() != ElevatorState.OUT_OF_SERVICE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Not in maintenance");
        }

        elevator.setState(ElevatorState.IDLE);
        elevator.setDirection(Direction.NONE);
        elevator.setDoorState(DoorState.CLOSED);
        elevator.setTargetFloor(null);
        elevator.setStateSince(Instant.now());
        return elevatorRepository.save(elevator);
    }

    // triggerEmergencyRecall moved onto POST /elevators/{id} in slice 7
    // (see feature.triggeremergencyrecall); the EMERGENCY_RECALL state
    // itself, and the other methods' guards against it above, stay --
    // this old model still represents the read side these methods
    // serve, and nothing in this slice touches that.

    private boolean isOverloaded(Elevator elevator) {
        return elevator.getCurrentWeightKg() > elevator.getWeightCapacityKg();
    }

    private void clearPendingCarCalls(Elevator elevator) {
        List<CarCall> pending = carCallRepository
                .findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(elevator.getId());
        Instant now = Instant.now();
        pending.forEach(cc -> {
            cc.setServedAt(now);
            carCallRepository.save(cc);
        });
    }

    private void clearPendingCalls(Elevator elevator) {
        List<Call> pending = callRepository
                .findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(elevator.getId());
        Instant now = Instant.now();
        pending.forEach(c -> {
            c.setServedAt(now);
            callRepository.save(c);
        });
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
        boolean changed;
        do {
            changed = false;
            long elapsedSeconds = Duration.between(elevator.getStateSince(), Instant.now()).getSeconds();

            if (elevator.getState() == ElevatorState.MOVING_UP
                    || elevator.getState() == ElevatorState.MOVING_DOWN
                    || elevator.getState() == ElevatorState.EMERGENCY_RECALL) {
                changed = recomputeMovement(elevator, elapsedSeconds);
            } else if (elevator.getState() == ElevatorState.DOORS_OPEN) {
                if (isOverloaded(elevator)) {
                    elevator.setStateSince(Instant.now());
                    elevatorRepository.save(elevator);
                } else if (elapsedSeconds >= properties.doorOpenTimeoutSeconds()) {
                    elevator.setDoorState(DoorState.CLOSING);
                    elevator.setState(ElevatorState.DOORS_CLOSING);
                    elevator.setStateSince(Instant.now());
                    elevatorRepository.save(elevator);
                    changed = true;
                }
            } else if (elevator.getState() == ElevatorState.DOORS_CLOSING) {
                if (elevator.isObstructed()) {
                    elevator.setDoorState(DoorState.OPEN);
                    elevator.setState(ElevatorState.DOORS_OPEN);
                    elevator.setStateSince(Instant.now());
                    elevatorRepository.save(elevator);
                    changed = true;
                } else if (elapsedSeconds >= DOOR_CLOSE_DURATION_SECONDS) {
                    elevator.setDoorState(DoorState.CLOSED);
                    elevator.setState(ElevatorState.IDLE);
                    elevator.setStateSince(Instant.now());
                    elevatorRepository.save(elevator);
                    changed = true;
                    serveNextPendingCall(elevator);
                }
            }
        } while (changed);
    }

    private boolean recomputeMovement(Elevator elevator, long elapsedSeconds) {
        int departureFloor = elevator.getDepartureFloor();
        int targetFloor = elevator.getTargetFloor();
        int sign = targetFloor > departureFloor ? 1 : -1;
        int distance = Math.abs(targetFloor - departureFloor);
        long floorsTraveled = elapsedSeconds / properties.travelSecondsPerFloor();

        if (floorsTraveled >= distance) {
            if (elevator.getState() == ElevatorState.EMERGENCY_RECALL) {
                elevator.setCurrentFloor(targetFloor);
                elevator.setState(ElevatorState.OUT_OF_SERVICE);
                elevator.setDirection(Direction.NONE);
                elevator.setDoorState(DoorState.CLOSED);
                elevator.setTargetFloor(null);
                elevator.setStateSince(Instant.now());
                elevatorRepository.save(elevator);
            } else {
                serveFloor(elevator, targetFloor);
            }
            return true;
        }

        int interimFloor = departureFloor + sign * (int) floorsTraveled;

        if (elevator.getState() != ElevatorState.EMERGENCY_RECALL) {
            Integer extraStop = findPendingFloorOnPath(elevator, departureFloor,
                    interimFloor, sign);
            if (extraStop != null) {
                serveFloor(elevator, extraStop);
                return true;
            }
        }

        elevator.setCurrentFloor(interimFloor);
        return false;
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
            elevator.setDepartureFloor(elevator.getCurrentFloor());
            elevator.setDirection(direction);
            elevator.setDoorState(DoorState.CLOSED);
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

        if (isOverloaded(elevator)) {
            clearPendingCarCalls(elevator);
            return;
        }

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
            elevator.setDepartureFloor(elevator.getCurrentFloor());
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
