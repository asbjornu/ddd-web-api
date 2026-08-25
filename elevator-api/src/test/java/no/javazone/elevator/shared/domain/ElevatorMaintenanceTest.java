package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElevatorMaintenanceTest {

    @Test
    void enterMaintenanceTransitionsToOutOfService() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);

        List<DomainEvent> events = elevator.enterMaintenance();

        assertThat(elevator.state()).isInstanceOf(ElevatorState.OutOfService.class);
        assertThat(events).hasAtLeastOneElementOfType(MaintenanceEntered.class);
    }

    @Test
    void enterMaintenanceClearsPendingRequestsAndReportsIt() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);
        elevator.call(new Floor(5), Direction.DOWN);
        elevator.selectFloor(new Floor(2));

        List<DomainEvent> events = elevator.enterMaintenance();

        assertThat(elevator.queue().pendingLandingCalls()).isEmpty();
        assertThat(elevator.queue().pendingCarCalls()).isEmpty();
        assertThat(events).hasAtLeastOneElementOfType(PendingRequestsCleared.class);
    }

    @Test
    void enterMaintenanceWithNothingPendingReportsNoClearing() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);

        List<DomainEvent> events = elevator.enterMaintenance();

        assertThat(events).noneMatch(PendingRequestsCleared.class::isInstance);
    }

    @Test
    void enterMaintenanceIsRefusedMidRecall() {
        Elevator elevator = Elevator.restore(
                new ElevatorId(1),
                new Floor(3),
                new ElevatorState.EmergencyRecall(new Floor(1)),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty());

        assertThatThrownBy(elevator::enterMaintenance).isInstanceOf(CommandRefused.class);
    }

    @Test
    void exitMaintenanceReturnsToIdle() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);
        elevator.enterMaintenance();

        List<DomainEvent> events = elevator.exitMaintenance();

        assertThat(elevator.state()).isInstanceOf(ElevatorState.Idle.class);
        assertThat(events).hasAtLeastOneElementOfType(MaintenanceExited.class);
    }

    @Test
    void exitMaintenanceIsRefusedWhenNotInMaintenance() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);

        assertThatThrownBy(elevator::exitMaintenance).isInstanceOf(CommandRefused.class);
    }
}
