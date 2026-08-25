package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElevatorLoadTest {

    private Elevator doorsOpenElevator() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);
        elevator.openDoors();
        return elevator;
    }

    @Test
    void reportingLoadWhileDoorsOpenUpdatesTheLoad() {
        Elevator elevator = doorsOpenElevator();

        List<DomainEvent> events = elevator.reportLoad(500);

        assertThat(elevator.load().kilograms()).isEqualTo(500);
        assertThat(elevator.load().capacityKilograms()).isEqualTo(800);
        assertThat(events).hasAtLeastOneElementOfType(LoadReported.class);
    }

    @Test
    void reportingLoadWhileDoorsClosedIsRefused() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);

        assertThatThrownBy(() -> elevator.reportLoad(500)).isInstanceOf(CommandRefused.class);
    }

    @Test
    void overloadedCarRefusesToSelectAFloorWithATypedProblem() {
        Elevator elevator = doorsOpenElevator();
        elevator.reportLoad(900);

        assertThatThrownBy(() -> elevator.selectFloor(new Floor(5)))
                .isInstanceOf(CommandRefused.class)
                .satisfies(ex -> assertThat(((CommandRefused) ex).type())
                        .isEqualTo("/problems/overloaded"));
    }

    @Test
    void overloadedCarRefusesToCloseDoors() {
        Elevator elevator = doorsOpenElevator();
        elevator.reportLoad(900);

        assertThatThrownBy(elevator::closeDoors).isInstanceOf(CommandRefused.class);
    }

    @Test
    void overloadedIdleCarDoesNotDispatchEvenWithAPendingCall() {
        Elevator elevator = doorsOpenElevator();
        elevator.reportLoad(900);

        // Force back to idle via restore to exercise dispatch() directly
        // with an overloaded car -- finishClosingIfStillClosing would
        // never reach idle while overloaded in practice (closeDoors and
        // autoCloseIfStillOpen both refuse/skip first).
        Elevator idleOverloaded = Elevator.restore(
                elevator.id(),
                elevator.currentFloor(),
                new ElevatorState.Idle(),
                elevator.doors(),
                elevator.load(),
                RequestQueue.empty());
        List<DomainEvent> events = idleOverloaded.call(new Floor(5), Direction.UP);

        assertThat(idleOverloaded.state()).isInstanceOf(ElevatorState.Idle.class);
        assertThat(events).noneMatch(MovementStarted.class::isInstance);
    }

    @Test
    void autoCloseDoesNothingWhileOverloaded() {
        Elevator elevator = doorsOpenElevator();
        elevator.reportLoad(900);

        assertThat(elevator.autoCloseIfStillOpen()).isEmpty();
    }
}
