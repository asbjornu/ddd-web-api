package no.javazone.elevator.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import no.javazone.elevator.model.Call;
import no.javazone.elevator.model.CarCall;
import no.javazone.elevator.model.Direction;
import no.javazone.elevator.model.DoorState;
import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.model.ElevatorState;
import no.javazone.elevator.repository.CallRepository;
import no.javazone.elevator.repository.CarCallRepository;
import no.javazone.elevator.repository.ElevatorRepository;

/**
 * Characterisation tests for {@link ElevatorService}.
 *
 * <p>These pin down what the service does today rather than what it ought to
 * do. That is the point: {@code ElevatorService} is the God Object this
 * repository exists to demonstrate, and its request-queue scheduling is the
 * most intricate thing in the codebase and the least directly covered. When
 * that logic moves onto a domain model, these tests are what establish that
 * behaviour was preserved rather than merely re-implemented.
 *
 * <p>Nothing here sleeps. The service derives state from elapsed wall-clock
 * time on every read, so a journey or a door timeout is simulated by setting
 * {@code stateSince} into the past and then reading the status once, which
 * advances the machine exactly one step.
 */
@SpringBootTest
@Transactional
class ElevatorServiceTest {

    private static final long ELEVATOR_ID = 1L;

    /** Longer than the service's hard-coded DOOR_CLOSE_DURATION_SECONDS. */
    private static final long PAST_DOOR_CLOSE = 5;

    @Autowired
    private ElevatorService service;

    @Autowired
    private ElevatorRepository elevators;

    @Autowired
    private CallRepository calls;

    @Autowired
    private CarCallRepository carCalls;

    private Elevator elevator;

    @BeforeEach
    void loadSeededElevator() {
        elevator = elevators.findById(ELEVATOR_ID).orElseThrow();
    }

    // ──────────────────────────────────────────────
    //  Dispatch from idle
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("an idle car goes to the floor just requested, not the nearest pending one")
    void idleDispatchTargetsTheRequestedFloor() {
        service.carCall(ELEVATOR_ID, carCallFor(8));
        service.carCall(ELEVATOR_ID, carCallFor(2));

        Elevator status = service.getStatus(ELEVATOR_ID);

        assertThat(status.getTargetFloor()).isEqualTo(8);
        assertThat(status.getState()).isEqualTo(ElevatorState.MOVING_UP);
    }

    // ──────────────────────────────────────────────
    //  Direction-committed scheduling (SCAN/LOOK)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("travelling up, the car serves the lowest pending floor above before reversing")
    void servesTheLowestPendingFloorAboveWhenTravellingUp() {
        parkAtWithDoorsClosing(5, Direction.UP);
        pendingCarCall(7);
        pendingCarCall(2);

        Elevator status = service.getStatus(ELEVATOR_ID);

        assertThat(status.getTargetFloor()).isEqualTo(7);
        assertThat(status.getState()).isEqualTo(ElevatorState.MOVING_UP);
    }

    @Test
    @DisplayName("travelling up with nothing left above, the car reverses to the highest below")
    void reversesOnlyWhenNothingRemainsAhead() {
        parkAtWithDoorsClosing(5, Direction.UP);
        pendingCarCall(2);
        pendingCarCall(4);

        Elevator status = service.getStatus(ELEVATOR_ID);

        assertThat(status.getTargetFloor()).isEqualTo(4);
        assertThat(status.getDirection()).isEqualTo(Direction.DOWN);
        assertThat(status.getState()).isEqualTo(ElevatorState.MOVING_DOWN);
    }

    @Test
    @DisplayName("travelling down, the car serves the highest pending floor below before reversing")
    void servesTheHighestPendingFloorBelowWhenTravellingDown() {
        parkAtWithDoorsClosing(5, Direction.DOWN);
        pendingCarCall(3);
        pendingCarCall(8);

        Elevator status = service.getStatus(ELEVATOR_ID);

        assertThat(status.getTargetFloor()).isEqualTo(3);
        assertThat(status.getState()).isEqualTo(ElevatorState.MOVING_DOWN);
    }

    @Test
    @DisplayName("with no committed direction, the car picks the nearest pending floor")
    void picksTheNearestPendingFloorWhenDirectionIsNone() {
        parkAtWithDoorsClosing(5, Direction.NONE);
        pendingCarCall(3);
        pendingCarCall(9);

        Elevator status = service.getStatus(ELEVATOR_ID);

        assertThat(status.getTargetFloor()).isEqualTo(3);
        assertThat(status.getDirection()).isEqualTo(Direction.DOWN);
    }

    // ──────────────────────────────────────────────
    //  Overload
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("an overloaded car clears its car calls and refuses to move")
    void overloadClearsCarCallsAndPreventsDeparture() {
        service.openDoors(ELEVATOR_ID);
        service.setWeight(ELEVATOR_ID, elevator.getWeightCapacityKg() + 40);

        assertThat(carCalls.findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(ELEVATOR_ID))
                .isEmpty();
        assertThat(service.getStatus(ELEVATOR_ID).getState())
                .isEqualTo(ElevatorState.DOORS_OPEN);
    }

    // ──────────────────────────────────────────────
    //  Key-switch actions
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("entering maintenance clears every pending request")
    void maintenanceClearsThePendingQueue() {
        service.call(ELEVATOR_ID, callFor(6, Direction.DOWN));
        pendingCarCall(3);

        service.enterMaintenance(ELEVATOR_ID);

        assertThat(calls.findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(ELEVATOR_ID))
                .isEmpty();
        assertThat(carCalls.findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(ELEVATOR_ID))
                .isEmpty();
        assertThat(service.getStatus(ELEVATOR_ID).getState())
                .isEqualTo(ElevatorState.OUT_OF_SERVICE);
    }

    @Test
    @DisplayName("exiting maintenance returns the car to idle")
    void exitMaintenanceReturnsToIdle() {
        service.enterMaintenance(ELEVATOR_ID);

        service.exitMaintenance(ELEVATOR_ID);

        assertThat(service.getStatus(ELEVATOR_ID).getState()).isEqualTo(ElevatorState.IDLE);
    }

    @Test
    @DisplayName("emergency recall pre-empts a journey already under way")
    void emergencyRecallPreEmptsTravel() {
        parkIdleAt(6);
        pendingCarCall(9);

        Elevator recalled = service.triggerEmergencyRecall(ELEVATOR_ID);

        assertThat(recalled.getState()).isEqualTo(ElevatorState.EMERGENCY_RECALL);
        assertThat(recalled.getTargetFloor()).isEqualTo(1);
        assertThat(recalled.getDirection()).isEqualTo(Direction.DOWN);
        assertThat(carCalls.findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(ELEVATOR_ID))
                .isEmpty();
    }

    @Test
    @DisplayName("emergency recall pre-empts maintenance too")
    void emergencyRecallPreEmptsMaintenance() {
        parkIdleAt(6);
        service.enterMaintenance(ELEVATOR_ID);

        Elevator recalled = service.triggerEmergencyRecall(ELEVATOR_ID);

        assertThat(recalled.getState()).isEqualTo(ElevatorState.EMERGENCY_RECALL);
    }

    @Test
    @DisplayName("recall at the recall floor skips EMERGENCY_RECALL and leaves the doors shut")
    void emergencyRecallAtTheRecallFloorGoesStraightOutOfService() {
        // The seeded car sits at floor 1, which is the recall floor.
        Elevator recalled = service.triggerEmergencyRecall(ELEVATOR_ID);

        assertThat(recalled.getState()).isEqualTo(ElevatorState.OUT_OF_SERVICE);
        assertThat(recalled.getTargetFloor()).isNull();

        // Recorded rather than endorsed. docs/architecture.md says recall
        // "opens its doors and then automatically transitions to
        // outOfService", but on this path the doors are set CLOSED and the
        // EMERGENCY_RECALL state is never entered at all -- so a client
        // watching for it would miss the transition entirely. Worth
        // resolving when recall moves onto the aggregate.
        assertThat(recalled.getDoorState()).isEqualTo(DoorState.CLOSED);
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────

    /**
     * Puts the car at {@code floor} with its doors closing and the close
     * already overdue, so that a single status read finishes the close and
     * runs the scheduler.
     */
    private void parkAtWithDoorsClosing(int floor, Direction direction) {
        elevator.setCurrentFloor(floor);
        elevator.setDepartureFloor(floor);
        elevator.setTargetFloor(null);
        elevator.setDirection(direction);
        elevator.setState(ElevatorState.DOORS_CLOSING);
        elevator.setDoorState(DoorState.CLOSING);
        elevator.setObstructed(false);
        elevator.setCurrentWeightKg(0);
        elevator.setStateSince(Instant.now().minus(PAST_DOOR_CLOSE, ChronoUnit.SECONDS));
        elevators.save(elevator);
    }

    /** Puts the car at {@code floor}, idle, doors shut, with nothing pending. */
    private void parkIdleAt(int floor) {
        elevator.setCurrentFloor(floor);
        elevator.setDepartureFloor(floor);
        elevator.setTargetFloor(null);
        elevator.setDirection(Direction.NONE);
        elevator.setState(ElevatorState.IDLE);
        elevator.setDoorState(DoorState.CLOSED);
        elevator.setObstructed(false);
        elevator.setCurrentWeightKg(0);
        elevator.setStateSince(Instant.now());
        elevators.save(elevator);
    }

    /** Records a car call directly, bypassing the dispatch that {@code carCall} performs. */
    private void pendingCarCall(int floor) {
        CarCall carCall = new CarCall();
        carCall.setElevatorId(ELEVATOR_ID);
        carCall.setFloor(floor);
        carCall.setCreatedAt(Instant.now());
        carCalls.save(carCall);
    }

    private static CarCall carCallFor(int floor) {
        CarCall request = new CarCall();
        request.setFloor(floor);
        return request;
    }

    private static Call callFor(int floor, Direction direction) {
        Call request = new Call();
        request.setFloor(floor);
        request.setDirection(direction);
        return request;
    }
}
