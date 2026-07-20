package no.javazone.elevator.model;

/**
 * The elevator's top-level state. See docs/architecture.md for the full
 * state machine description, including how outOfService and
 * emergencyRecall pre-empt normal operation.
 */
public enum ElevatorState {
    IDLE,
    DOORS_OPEN,
    DOORS_CLOSING,
    MOVING_UP,
    MOVING_DOWN,
    OUT_OF_SERVICE,
    EMERGENCY_RECALL
}
