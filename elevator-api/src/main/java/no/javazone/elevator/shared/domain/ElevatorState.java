package no.javazone.elevator.shared.domain;

/**
 * The elevator's top-level state, sealed so that each state answers for
 * itself instead of a caller switching on a status string -- see the
 * "Switch Statements" smell this replaces, and
 * {@code docs/architecture.md}'s "Elevator state" section for the full
 * description of each state.
 *
 * <p>{@code EmergencyRecall} pre-empts every other state and always
 * settles into {@code OutOfService} on arrival; {@code OutOfService} is
 * otherwise reached only via an explicit {@code EnterMaintenance} command
 * (a future slice) and left only via {@code ExitMaintenance}.
 */
public sealed interface ElevatorState {

    record Idle() implements ElevatorState {
    }

    record DoorsOpen() implements ElevatorState {
    }

    record DoorsClosing() implements ElevatorState {
    }

    record MovingUp(Floor destination) implements ElevatorState {
    }

    record MovingDown(Floor destination) implements ElevatorState {
    }

    record OutOfService() implements ElevatorState {
    }

    record EmergencyRecall(Floor recallFloor) implements ElevatorState {
    }
}
