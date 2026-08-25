package no.javazone.elevator.shared.domain;

/**
 * Thrown when a command is refused by domain rule rather than by a
 * malformed request -- see {@code docs/plan.html} &sect;6's "When the
 * answer is no": a rejected command should say something about the
 * domain ("the car is in motion"), not just that the payload was wrong.
 * The web layer translates this into a 409 Problem carrying the message
 * as {@code detail}.
 *
 * <p>{@code type} defaults to {@code about:blank} -- RFC 9457's own
 * default, for a refusal generic enough that no reader needs more than
 * the status code. A refusal worth a client actually branching on
 * (overload, so far) carries its own type URI instead; see
 * {@code docs/plan.html} &sect;6's "When the answer is no" for the
 * restraint rule this follows: "truly generic problems... are usually
 * better expressed as plain status codes".
 */
public class CommandRefused extends RuntimeException {

    private final String type;

    public CommandRefused(String message) {
        this(message, "about:blank");
    }

    public CommandRefused(String message, String type) {
        super(message);
        this.type = type;
    }

    public String type() {
        return type;
    }
}
