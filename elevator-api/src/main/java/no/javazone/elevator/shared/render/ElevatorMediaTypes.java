package no.javazone.elevator.shared.render;

import org.springframework.http.MediaType;

/**
 * The custom media types this API negotiates alongside the IANA-registered
 * ones ({@code text/html}, {@code application/ld+json}) -- see
 * {@code docs/plan.html} &sect;7's "Four media types, one affordance
 * model".
 */
public final class ElevatorMediaTypes {

    /**
     * Kept only as a teaching device (see {@code docs/plan.html} &sect;18):
     * a minimal, bespoke format, not a recommendation.
     */
    public static final MediaType ELEVATOR_STATE_JSON =
            new MediaType("application", "vnd.elevator.state+json");

    public static final MediaType SIREN_JSON =
            new MediaType("application", "vnd.siren+json");

    public static final MediaType JSON_LD =
            new MediaType("application", "ld+json");

    public static final MediaType PROBLEM_JSON =
            new MediaType("application", "problem+json");

    private ElevatorMediaTypes() {
    }
}
