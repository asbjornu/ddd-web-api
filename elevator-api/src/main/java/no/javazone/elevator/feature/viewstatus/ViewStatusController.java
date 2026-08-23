package no.javazone.elevator.feature.viewstatus;

import java.util.Optional;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.web.RepresentationResponses;
import no.javazone.elevator.shared.web.UriResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /elevators/{id}}: the read side's own resource, replacing
 * the old {@code GET /elevators/{id}/status}. Queries never touch the
 * write-side aggregate -- this reads only {@link ElevatorViewProjection}
 * -- see {@code docs/architecture.md}'s "CQRS and domain events" section.
 *
 * <p>Affordances are computed from the read model's own {@code state}
 * string via {@link AffordanceContext}, never from the write-side
 * aggregate -- the query side has no reason to depend on it.
 */
@RestController
public class ViewStatusController {

    private final ElevatorViewProjection projection;
    private final UriResolver uriResolver;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;

    public ViewStatusController(
            ElevatorViewProjection projection,
            UriResolver uriResolver,
            AffordanceCatalog affordanceCatalog,
            RepresentationResponses responses) {
        this.projection = projection;
        this.uriResolver = uriResolver;
        this.affordanceCatalog = affordanceCatalog;
        this.responses = responses;
    }

    @GetMapping("/elevators/{segment}")
    public ResponseEntity<String> view(
            @PathVariable String segment,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Optional<ElevatorId> id = resolve(segment);
        Optional<ElevatorView> view = id.flatMap(projection::find);
        if (view.isEmpty()) {
            return responses.problem(HttpStatus.NOT_FOUND, accept, notFound(segment));
        }
        return responses.ok(accept, representation(segment, view.get()));
    }

    private Optional<ElevatorId> resolve(String segment) {
        try {
            return Optional.of(uriResolver.resolve(segment));
        } catch (RuntimeException invalidSegment) {
            return Optional.empty();
        }
    }

    private Representation representation(String segment, ElevatorView view) {
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

    private Representation notFound(String segment) {
        return Representation.builder("Not Found")
                .property("type", "about:blank")
                .property("title", "Not Found")
                .property("status", 404)
                .property("detail", "No elevator known by the identifier \"" + segment + "\".")
                .build();
    }
}
