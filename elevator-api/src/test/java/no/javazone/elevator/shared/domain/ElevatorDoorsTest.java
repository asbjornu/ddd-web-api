package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElevatorDoorsTest {

    private Elevator idleElevator() {
        return Elevator.seed(new ElevatorId(1), new Floor(1), 800);
    }

    @Test
    void givenIdle_whenDoorsOpened_thenStateBecomesDoorsOpen() {
        Elevator elevator = idleElevator();

        List<DomainEvent> events = elevator.openDoors();

        assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsOpen.class);
        assertThat(elevator.doors().position()).isEqualTo(Doors.DoorPosition.OPEN);
        assertThat(events).hasAtLeastOneElementOfType(DoorsOpened.class);
    }

    @Test
    void givenMoving_whenOpenDoors_thenRefused() {
        Elevator elevator = idleElevator();
        elevator.call(new Floor(5), Direction.UP);

        assertThatThrownBy(elevator::openDoors).isInstanceOf(CommandRefused.class);
    }

    @Test
    void givenDoorsOpen_whenClosed_thenStateBecomesDoorsClosing() {
        Elevator elevator = idleElevator();
        elevator.openDoors();

        List<DomainEvent> events = elevator.closeDoors();

        assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsClosing.class);
        assertThat(elevator.doors().position()).isEqualTo(Doors.DoorPosition.CLOSING);
        assertThat(events).hasAtLeastOneElementOfType(DoorsClosingStarted.class);
    }

    @Test
    void givenDoorsNotOpen_whenClosed_thenRefused() {
        Elevator elevator = idleElevator();

        assertThatThrownBy(elevator::closeDoors).isInstanceOf(CommandRefused.class);
    }

    @Test
    void givenObstructed_whenClosed_thenRefused() {
        Elevator elevator = idleElevator();
        elevator.openDoors();
        elevator.closeDoors();
        elevator.obstructDoors();

        assertThatThrownBy(elevator::closeDoors).isInstanceOf(CommandRefused.class);
        // The doors stay open, which is what lets the affordance catalog
        // still offer open-doors on the refusal's own representation --
        // see this slice's commit message.
        assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsOpen.class);
    }

    @Test
    void givenClosing_whenObstructed_thenDoorsReopen() {
        Elevator elevator = idleElevator();
        elevator.openDoors();
        elevator.closeDoors();

        List<DomainEvent> events = elevator.obstructDoors();

        assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsOpen.class);
        assertThat(elevator.doors().obstructed()).isTrue();
        assertThat(events).hasAtLeastOneElementOfType(DoorsObstructed.class);
        assertThat(events).hasAtLeastOneElementOfType(DoorsOpened.class);
    }

    @Test
    void givenNotClosing_whenObstructed_thenRefused() {
        Elevator elevator = idleElevator();

        assertThatThrownBy(elevator::obstructDoors).isInstanceOf(CommandRefused.class);
    }

    @Test
    void givenObstructed_whenCleared_thenNoLongerObstructed() {
        Elevator elevator = idleElevator();
        elevator.openDoors();
        elevator.closeDoors();
        elevator.obstructDoors();

        List<DomainEvent> events = elevator.clearObstruction();

        assertThat(elevator.doors().obstructed()).isFalse();
        assertThat(events).hasAtLeastOneElementOfType(ObstructionCleared.class);
    }

    @Test
    void givenNotObstructed_whenCleared_thenRefused() {
        Elevator elevator = idleElevator();

        assertThatThrownBy(elevator::clearObstruction).isInstanceOf(CommandRefused.class);
    }

    @Test
    void autoCloseDoesNothingWhenAlreadyClosedByCommand() {
        Elevator elevator = idleElevator();
        elevator.openDoors();
        elevator.closeDoors();

        assertThat(elevator.autoCloseIfStillOpen()).isEmpty();
    }

    @Test
    void autoCloseTransitionsToClosingWhenStillOpen() {
        Elevator elevator = idleElevator();
        elevator.openDoors();

        List<DomainEvent> events = elevator.autoCloseIfStillOpen();

        assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsClosing.class);
        assertThat(events).hasAtLeastOneElementOfType(DoorsClosingStarted.class);
    }

    @Test
    void autoCloseDoesNothingWhileObstructed() {
        Elevator elevator = idleElevator();
        elevator.openDoors();
        elevator.closeDoors();
        elevator.obstructDoors();

        assertThat(elevator.autoCloseIfStillOpen()).isEmpty();
    }

    @Test
    void finishClosingDoesNothingIfObstructionAlreadyReopened() {
        Elevator elevator = idleElevator();
        elevator.openDoors();
        elevator.closeDoors();
        elevator.obstructDoors();

        assertThat(elevator.finishClosingIfStillClosing()).isEmpty();
        assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsOpen.class);
    }

    @Test
    void finishClosingReturnsToIdleWhenUninterrupted() {
        Elevator elevator = idleElevator();
        elevator.openDoors();
        elevator.closeDoors();

        List<DomainEvent> events = elevator.finishClosingIfStillClosing();

        assertThat(elevator.state()).isInstanceOf(ElevatorState.Idle.class);
        assertThat(elevator.doors().position()).isEqualTo(Doors.DoorPosition.CLOSED);
        assertThat(events).hasAtLeastOneElementOfType(DoorsClosed.class);
    }

    @Test
    void finishClosingDispatchesAPendingCall() {
        Elevator elevator = idleElevator();
        elevator.openDoors();
        // Queue a call while doors are open/closing -- dispatch only
        // happens from idle, so it waits for finishClosingIfStillClosing.
        elevator.call(new Floor(5), Direction.UP);
        elevator.closeDoors();

        List<DomainEvent> events = elevator.finishClosingIfStillClosing();

        assertThat(elevator.state()).isInstanceOfSatisfying(
                ElevatorState.MovingUp.class,
                moving -> assertThat(moving.destination()).isEqualTo(new Floor(5)));
        assertThat(events).hasAtLeastOneElementOfType(MovementStarted.class);
    }

    @Test
    void arrivingOpensTheDoorsAndEmitsDoorsOpened() {
        Elevator elevator = idleElevator();
        elevator.call(new Floor(5), Direction.UP);

        List<DomainEvent> events = elevator.passFloor(new Floor(5));

        assertThat(elevator.state()).isInstanceOf(ElevatorState.DoorsOpen.class);
        assertThat(events).hasAtLeastOneElementOfType(FloorReached.class);
        assertThat(events).hasAtLeastOneElementOfType(DoorsOpened.class);
    }
}
