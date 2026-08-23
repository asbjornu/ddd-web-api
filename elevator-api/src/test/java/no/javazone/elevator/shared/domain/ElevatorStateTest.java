package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A sealed interface's exhaustiveness is enforced by the compiler; this
 * test only pins down the shape (each state answers for itself, none of
 * them are interchangeable) so a future edit here is deliberate.
 */
class ElevatorStateTest {

    @Test
    void eachStateIsItsOwnType() {
        ElevatorState idle = new ElevatorState.Idle();
        ElevatorState movingUp = new ElevatorState.MovingUp(new Floor(5));
        ElevatorState recall = new ElevatorState.EmergencyRecall(new Floor(1, true));

        assertThat(idle).isNotEqualTo(movingUp);
        assertThat(switchOnState(idle)).isEqualTo("idle");
        assertThat(switchOnState(movingUp)).isEqualTo("moving");
        assertThat(switchOnState(recall)).isEqualTo("recall");
    }

    // Exercises exhaustive pattern matching over the sealed hierarchy,
    // the replacement for a switch on a status string/enum.
    private String switchOnState(ElevatorState state) {
        return switch (state) {
            case ElevatorState.Idle idle -> "idle";
            case ElevatorState.DoorsOpen doorsOpen -> "doorsOpen";
            case ElevatorState.DoorsClosing doorsClosing -> "doorsClosing";
            case ElevatorState.MovingUp movingUp -> "moving";
            case ElevatorState.MovingDown movingDown -> "moving";
            case ElevatorState.OutOfService outOfService -> "outOfService";
            case ElevatorState.EmergencyRecall emergencyRecall -> "recall";
        };
    }
}
