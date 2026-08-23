package no.javazone.elevator.shared.domain;

/**
 * Thrown when a command is refused by domain rule rather than by a
 * malformed request -- see {@code docs/plan.html} &sect;6's "When the
 * answer is no": a rejected command should say something about the
 * domain ("the car is in motion"), not just that the payload was wrong.
 * The web layer translates this into a 409 Problem carrying the message
 * as {@code detail}.
 */
public class CommandRefused extends RuntimeException {

    public CommandRefused(String message) {
        super(message);
    }
}
