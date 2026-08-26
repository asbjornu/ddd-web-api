package no.javazone.elevator.shared.security;

/**
 * The one cookie this API ever sets: a technician's own access token,
 * mirrored into an {@code HttpOnly} cookie by
 * {@code KeySwitchSessionController} so that a plain HTML form -- which
 * cannot attach an {@code Authorization} header of its own -- can still
 * complete a technician command. See {@code docs/architecture.md}'s
 * "elevator-ui: front-end only, no BFF" section and {@code
 * docs/plan.html} &sect;12's "Keeping the cookie stateless": the cookie
 * carries the token itself, verified by signature on every request like
 * any other Bearer token, with nothing stored server-side.
 */
public final class TechnicianSessionCookie {

    public static final String NAME = "technician_token";

    /** Scoped to where a technician's own requests go -- narrower than
     * {@code /}, since nothing outside the elevator resource ever needs
     * to see it. */
    public static final String PATH = "/elevators";

    private TechnicianSessionCookie() {
    }
}
