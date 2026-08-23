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

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOfSatisfying(ElevatorCalled.class, event -> {
            assertThat(event.floor()).isEqualTo(new Floor(5));
            assertThat(event.direction()).isEqualTo(Direction.UP);
            assertThat(event.elevatorId()).isEqualTo(elevator.id());
        });
    }

    @Test
    void givenIdle_whenCalled_thenTheCallIsQueued() {
        Elevator elevator = idleElevator();

        elevator.call(new Floor(5), Direction.UP);

        assertThat(elevator.queue().pendingLandingCalls())
                .containsExactly(new LandingCall(new Floor(5), Direction.UP));
    }

    @Test
    void givenIdle_whenCalled_thenStateDoesNotChange() {
        Elevator elevator = idleElevator();

        elevator.call(new Floor(5), Direction.UP);

        assertThat(elevator.state()).isInstanceOf(ElevatorState.Idle.class);
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
}
