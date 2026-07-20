package no.javazone.elevator.model;

/** The door's own little state machine, nested inside the elevator's. */
public enum DoorState {
    OPEN,
    CLOSING,
    CLOSED
}
