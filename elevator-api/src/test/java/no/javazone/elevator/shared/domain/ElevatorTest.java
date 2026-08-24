package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Given/When/Then, in the spirit of the Event Modeling specification
 * docs/architecture.md's "CQRS and domain events" section follows --
 * see docs/plan.html section 11.
 */
class ElevatorTest {

    private Elevator idleElevator() {
        return Elevator.seed(new ElevatorId(1), new Floor(1), 800);
    }

    private Elevator outOfServiceElevator() {
        return Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.OutOfService(),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty());
    }

    @Test
    void givenIdle_whenCalled_thenElevatorCalledIsEmitted() {
        Elevator elevator = idleElevator();

        List<DomainEvent> events = elevator.call(new Floor(5), Direction.UP);

        assertThat(events).hasAtLeastOneElementOfType(ElevatorCalled.class);
        ElevatorCalled called = events.stream()
                .filter(ElevatorCalled.class::isInstance)
                .map(ElevatorCalled.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(called.floor()).isEqualTo(new Floor(5));
        assertThat(called.direction()).isEqualTo(Direction.UP);
        assertThat(called.elevatorId()).isEqualTo(elevator.id());
    }

    @Test
    void givenIdle_whenCalled_thenTheCallIsQueued() {
        Elevator elevator = idleElevator();

        elevator.call(new Floor(5), Direction.UP);

        assertThat(elevator.queue().pendingLandingCalls())
                .containsExactly(new LandingCall(new Floor(5), Direction.UP));
    }

    @Test
    void givenIdleWithSomewhereToGo_whenCalled_thenTheCarDispatches() {
        Elevator elevator = idleElevator();

        List<DomainEvent> events = elevator.call(new Floor(5), Direction.UP);

        assertThat(elevator.state()).isInstanceOfSatisfying(
                ElevatorState.MovingUp.class,
                moving -> assertThat(moving.destination()).isEqualTo(new Floor(5)));
        assertThat(events).hasAtLeastOneElementOfType(MovementStarted.class);
    }

    @Test
    void givenOutOfService_whenCalled_thenTheCommandIsRefused() {
        Elevator elevator = outOfServiceElevator();

        assertThatThrownBy(() -> elevator.call(new Floor(5), Direction.UP))
                .isInstanceOf(CommandRefused.class);
    }

    @Test
    void givenEmergencyRecall_whenCalled_thenTheCommandIsRefused() {
        Elevator elevator = Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.EmergencyRecall(new Floor(1, true)),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty());

        assertThatThrownBy(() -> elevator.call(new Floor(5), Direction.UP))
                .isInstanceOf(CommandRefused.class);
    }

    @Test
    void givenIdle_whenFloorSelected_thenTheCarDispatchesDownwards() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(6), 800);

        List<DomainEvent> events = elevator.selectFloor(new Floor(2));

        assertThat(events).hasAtLeastOneElementOfType(FloorSelected.class);
        assertThat(elevator.state()).isInstanceOfSatisfying(
                ElevatorState.MovingDown.class,
                moving -> assertThat(moving.destination()).isEqualTo(new Floor(2)));
    }

    @Test
    void givenOverloaded_whenFloorSelected_thenTheCommandIsRefused() {
        Elevator elevator = Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.Idle(),
                Doors.closed(),
                new Load(900, 800),
                RequestQueue.empty());

        assertThatThrownBy(() -> elevator.selectFloor(new Floor(5)))
                .isInstanceOf(CommandRefused.class);
    }

    @Test
    void givenSelectingTheCurrentFloor_thenNothingDispatches() {
        Elevator elevator = idleElevator();

        List<DomainEvent> events = elevator.selectFloor(new Floor(1));

        assertThat(events).noneMatch(MovementStarted.class::isInstance);
        assertThat(elevator.state()).isInstanceOf(ElevatorState.Idle.class);
    }

    @Test
    void givenMoving_whenArrived_thenDoorsOpenAtTheDestination() {
        Elevator elevator = idleElevator();
        elevator.call(new Floor(5), Direction.UP);

        List<DomainEvent> events = elevator.arrive(new Floor(5));

        assertThat(elevator.currentFloor()).isEqualTo(new Floor(5));
        assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsOpen.class);
        assertThat(elevator.doors().position()).isEqualTo(Doors.DoorPosition.OPEN);
        assertThat(events).hasAtLeastOneElementOfType(FloorReached.class);
    }

    @Test
    void givenArrived_thenTheCallAtThatFloorIsCleared() {
        Elevator elevator = idleElevator();
        elevator.call(new Floor(5), Direction.UP);

        elevator.arrive(new Floor(5));

        assertThat(elevator.queue().pendingLandingCalls()).isEmpty();
    }

    @Test
    void givenIdleWithNoCalls_whenSecondCallArrivesWhileMoving_thenItDoesNotRetarget() {
        Elevator elevator = idleElevator();
        elevator.call(new Floor(7), Direction.UP);

        // Floor 3 is nearer, but the car is already committed to 7 --
        // see RequestQueue's own Javadoc on this simplification.
        elevator.call(new Floor(3), Direction.UP);

        assertThat(elevator.state()).isInstanceOfSatisfying(
                ElevatorState.MovingUp.class,
                moving -> assertThat(moving.destination()).isEqualTo(new Floor(7)));
    }
}
