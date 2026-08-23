package no.javazone.elevator.shared.web;

import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;

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
            String segment, ElevatorView view, AffordanceCatalog affordanceCatalog) {
        String self = "/elevators/" + segment;
        return Representation.builder("Elevator")
                .property("currentFloor", view.currentFloor())
                .property("state", view.state())
                .property("direction", view.direction())
                .property("doorPosition", view.doorPosition())
                .property("obstructed", view.obstructed())
                .property("weightKg", view.weightKg())
                .property("capacityKg", view.capacityKg())
                .link(new Link("self", self))
                .link(new Link("updates", self + "/events", "text/event-stream"))
                .affordances(affordanceCatalog.affordances(
                        AffordanceContext.forElevator(segment, view.state())))
                .build();
    }

    public static Representation eventRepresentation(ElevatorView view) {
        return Representation.builder("Elevator")
                .property("currentFloor", view.currentFloor())
                .property("state", view.state())
                .property("direction", view.direction())
                .property("doorPosition", view.doorPosition())
                .property("obstructed", view.obstructed())
                .property("weightKg", view.weightKg())
                .property("capacityKg", view.capacityKg())
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

    public static Representation conflict(String segment, String detail) {
        return Representation.builder("Conflict")
                .property("type", "about:blank")
                .property("title", "Conflict")
                .property("status", 409)
                .property("detail", detail)
                .link(new Link("self", "/elevators/" + segment))
                .build();
    }
}
