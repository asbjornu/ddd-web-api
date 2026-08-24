package no.javazone.elevator.shared.domain;

/**
 * The wire vocabulary for {@link ElevatorState}: a lower camelCase name
 * per permitted subtype, used by both the write side's snapshot
 * ({@code shared.persistence}) and the read side's view
 * ({@code feature.viewstatus}) so the mapping is written down exactly
 * once. Movement states additionally carry a destination floor, which a
 * caller must supply separately (see {@link #toDestination}) since a
 * bare name cannot.
 */
public final class ElevatorStateNames {

    private ElevatorStateNames() {
    }

    public static String of(ElevatorState state) {
        return switch (state) {
            case ElevatorState.Idle s -> "idle";
            case ElevatorState.DoorsOpen s -> "doorsOpen";
            case ElevatorState.DoorsClosing s -> "doorsClosing";
            case ElevatorState.MovingUp s -> "movingUp";
            case ElevatorState.MovingDown s -> "movingDown";
            case ElevatorState.OutOfService s -> "outOfService";
            case ElevatorState.EmergencyRecall s -> "emergencyRecall";
        };
    }

    /** The read model's own "direction" field: derived from the state, never stored separately. */
    public static String directionOf(ElevatorState state) {
        return switch (state) {
            case ElevatorState.MovingUp s -> "up";
            case ElevatorState.MovingDown s -> "down";
            default -> "none";
        };
    }

    /**
     * Reconstructs a state from its name, and -- for {@code movingUp} /
     * {@code movingDown} -- the destination floor a caller must already
     * know (there is nowhere else for it to come from). {@code
     * emergencyRecall} is not yet reachable by any command and is not
     * handled here; that arrives with slice 7.
     */
    public static ElevatorState fromName(String name, Floor destination) {
        return switch (name) {
            case "idle" -> new ElevatorState.Idle();
            case "doorsOpen" -> new ElevatorState.DoorsOpen();
            case "doorsClosing" -> new ElevatorState.DoorsClosing();
            case "outOfService" -> new ElevatorState.OutOfService();
            case "movingUp" -> new ElevatorState.MovingUp(requireDestination(name, destination));
            case "movingDown" -> new ElevatorState.MovingDown(requireDestination(name, destination));
            default -> throw new IllegalStateException(
                    "Cannot restore elevator state \"" + name + "\" yet");
        };
    }

    private static Floor requireDestination(String name, Floor destination) {
        if (destination == null) {
            throw new IllegalStateException(
                    "State \"" + name + "\" requires a destination floor to restore");
        }
        return destination;
    }
}
