package no.javazone.elevator.shared.web;

import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.security.Principal;

/**
 * The one shape every command endpoint's success and failure responses
 * take, factored out from the start now that {@code call-elevator} is
 * the first of what will be several slices sharing
 * {@code POST /elevators/{id}} (see {@link CommandsController}) rather
 * than each owning its own URL.
 */
public final class ElevatorRepresentations {

    private ElevatorRepresentations() {
    }

    public static Representation representation(
            String segment, ElevatorView view, AffordanceCatalog affordanceCatalog,
            Principal principal, ElevatorProperties properties) {
        String self = "/elevators/" + segment;
        return Representation.builder("Elevator")
                .property("currentFloor", view.currentFloor())
                .property("state", view.state())
                .property("direction", view.direction())
                .property("doorPosition", view.doorPosition())
                .property("obstructed", view.obstructed())
                .property("weightKg", view.weightKg())
                .property("capacityKg", view.capacityKg())
                .property("destinationFloor", view.destinationFloor())
                // Rides along on every representation, not just while a
                // trip is in progress, so a client already has this the
                // instant a command's own response reports movingUp/
                // movingDown -- never a client-side guessed-physics
                // constant, per docs/architecture.md's rule against
                // hard-coding travel timing; see ElevatorShaft.vue's own
                // startCarAnimation.
                .property("travelSecondsPerFloor", properties.travelSecondsPerFloor())
                .link(new Link("self", self))
                .link(new Link("updates", self + "/events", "text/event-stream"))
                .affordances(affordanceCatalog.affordances(AffordanceContext.forElevator(
                        segment, view.state(), view.obstructed(), view.weightKg() > view.capacityKg(),
                        principal)))
                .build();
    }

    public static Representation eventRepresentation(ElevatorView view, ElevatorProperties properties) {
        return Representation.builder("Elevator")
                .property("currentFloor", view.currentFloor())
                .property("state", view.state())
                .property("direction", view.direction())
                .property("doorPosition", view.doorPosition())
                .property("obstructed", view.obstructed())
                .property("weightKg", view.weightKg())
                .property("capacityKg", view.capacityKg())
                .property("destinationFloor", view.destinationFloor())
                .property("travelSecondsPerFloor", properties.travelSecondsPerFloor())
                .build();
    }

    public static Representation notFound(String segment) {
        return Representation.builder("Not Found")
                .property("type", "about:blank")
                .property("title", "Not Found")
                .property("status", 404)
                .property("detail", "No elevator known by the identifier \"" + segment + "\".")
                .build();
    }

    public static Representation badRequest(String detail) {
        return Representation.builder("Bad Request")
                .property("type", "about:blank")
                .property("title", "Bad Request")
                .property("status", 400)
                .property("detail", detail)
                .build();
    }

    /** A bare 403, with no invented problem type -- an unauthorised
     * command looks like this, not like a domain refusal: it never got
     * far enough to be one. See {@code docs/architecture.md}'s
     * "Key-switch and authorization" section: the affordance was simply
     * absent for this caller, and this is what following a URL nobody
     * offered them gets back. */
    public static Representation forbidden(String detail) {
        return Representation.builder("Forbidden")
                .property("type", "about:blank")
                .property("title", "Forbidden")
                .property("status", 403)
                .property("detail", detail)
                .build();
    }

    public static Representation conflict(String segment, String detail) {
        return Representation.builder("Conflict")
                .property("type", "about:blank")
                .property("title", "Conflict")
                .property("status", 409)
                .property("detail", detail)
                .link(new Link("self", "/elevators/" + segment))
                .build();
    }

    /** Uses {@code refused}'s own {@code type} -- {@code about:blank} for a
     * generic refusal, or a specific problem URI (e.g. overload) -- rather
     * than hard-coding one, per {@code docs/architecture.md}'s slice 5
     * roadmap entry. */
    public static Representation conflict(String segment, CommandRefused refused) {
        return Representation.builder("Conflict")
                .property("type", refused.type())
                .property("title", "Conflict")
                .property("status", 409)
                .property("detail", refused.getMessage())
                .link(new Link("self", "/elevators/" + segment))
                .build();
    }

    /** Same as {@link #conflict(String, String)}, but also carries the
     * elevator's current affordances -- close-doors's refusal is the one
     * place a rider needs to be told, in the same response, that
     * {@code open-doors} is still there; see
     * {@code docs/plan.html} &sect;6's "a refusal carries affordances too". */
    public static Representation conflict(
            String segment, String detail, ElevatorView current, AffordanceCatalog affordanceCatalog,
            Principal principal) {
        return Representation.builder("Conflict")
                .property("type", "about:blank")
                .property("title", "Conflict")
                .property("status", 409)
                .property("detail", detail)
                .link(new Link("self", "/elevators/" + segment))
                .affordances(affordanceCatalog.affordances(AffordanceContext.forElevator(
                        segment, current.state(), current.obstructed(),
                        current.weightKg() > current.capacityKg(), principal)))
                .build();
    }
}
