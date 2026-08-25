package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElevatorEmergencyRecallTest {

    @Test
    void triggerEmergencyRecallTransitionsDirectlyToOutOfServiceWhenAlreadyAtRecallFloor() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);

        List<DomainEvent> events = elevator.triggerEmergencyRecall(new Floor(1, true));

        assertThat(elevator.state()).isInstanceOf(ElevatorState.OutOfService.class);
        assertThat(events).hasAtLeastOneElementOfType(EmergencyRecallTriggered.class);
        assertThat(events).hasAtLeastOneElementOfType(EmergencyRecallCompleted.class);
    }

    @Test
    void triggerEmergencyRecallTransitionsToEmergencyRecallWhenElsewhere() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(3), 800);

        List<DomainEvent> events = elevator.triggerEmergencyRecall(new Floor(1, true));

        assertThat(elevator.state())
                .isEqualTo(new ElevatorState.EmergencyRecall(new Floor(1, true)));
        assertThat(events).hasAtLeastOneElementOfType(EmergencyRecallTriggered.class);
        assertThat(events).noneMatch(EmergencyRecallCompleted.class::isInstance);
    }

    @Test
    void triggerEmergencyRecallClearsPendingRequestsAndReportsIt() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(3), 800);
        elevator.call(new Floor(5), Direction.DOWN);
        elevator.selectFloor(new Floor(2));

        List<DomainEvent> events = elevator.triggerEmergencyRecall(new Floor(1, true));

        assertThat(elevator.queue().pendingLandingCalls()).isEmpty();
        assertThat(elevator.queue().pendingCarCalls()).isEmpty();
        assertThat(events).hasAtLeastOneElementOfType(PendingRequestsCleared.class);
    }

    @Test
    void triggerEmergencyRecallIsNeverRefusedEvenWhileMidRecallOrInMaintenance() {
        Elevator alreadyRecalling = Elevator.restore(
                new ElevatorId(1),
                new Floor(3),
                new ElevatorState.EmergencyRecall(new Floor(1, true)),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty());

        List<DomainEvent> events = alreadyRecalling.triggerEmergencyRecall(new Floor(1, true));

        assertThat(events).hasAtLeastOneElementOfType(EmergencyRecallTriggered.class);

        Elevator inMaintenance = Elevator.seed(new ElevatorId(1), new Floor(1), 800);
        inMaintenance.enterMaintenance();

        assertThat(inMaintenance.triggerEmergencyRecall(new Floor(1, true)))
                .hasAtLeastOneElementOfType(EmergencyRecallTriggered.class);
    }

    @Test
    void triggerEmergencyRecallClosesTheDoors() {
        Elevator elevator = Elevator.restore(
                new ElevatorId(1),
                new Floor(3),
                new ElevatorState.DoorsOpen(),
                new Doors(Doors.DoorPosition.OPEN, false),
                new Load(0, 800),
                RequestQueue.empty());

        elevator.triggerEmergencyRecall(new Floor(1, true));

        assertThat(elevator.doors().position()).isEqualTo(Doors.DoorPosition.CLOSED);
    }

    @Test
    void completeEmergencyRecallSettlesIntoOutOfServiceAtTheRecallFloor() {
        Elevator elevator = Elevator.restore(
                new ElevatorId(1),
                new Floor(3),
                new ElevatorState.EmergencyRecall(new Floor(1, true)),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty());

        List<DomainEvent> events = elevator.completeEmergencyRecall();

        assertThat(elevator.currentFloor()).isEqualTo(new Floor(1, true));
        assertThat(elevator.state()).isInstanceOf(ElevatorState.OutOfService.class);
        assertThat(events).hasAtLeastOneElementOfType(EmergencyRecallCompleted.class);
    }

    @Test
    void completeEmergencyRecallIsANoOpWhenNotMidRecall() {
        Elevator elevator = Elevator.seed(new ElevatorId(1), new Floor(1), 800);

        List<DomainEvent> events = elevator.completeEmergencyRecall();

        assertThat(events).isEmpty();
        assertThat(elevator.state()).isInstanceOf(ElevatorState.Idle.class);
    }
}
