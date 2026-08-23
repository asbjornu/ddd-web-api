package no.javazone.elevator.shared.domain;

/**
 * The doors' own state, independent of {@link ElevatorState}: doors have
 * a small nested state machine of their own (open, closing, closed;
 * obstruction re-opens), which is why it is a value object rather than a
 * pair of booleans scattered across the aggregate.
 */
public record Doors(DoorPosition position, boolean obstructed) {

    public enum DoorPosition {
        OPEN,
        CLOSING,
        CLOSED
    }

    public static Doors closed() {
        return new Doors(DoorPosition.CLOSED, false);
    }
}
