package no.javazone.elevator.feature.streamevents;

import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.render.ElevatorStateJsonRenderer;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.web.UriResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /elevators/{id}/events}: one stream that speaks only when
 * something happened, replacing the 1.5 second poll -- see
 * {@code docs/plan.html} &sect;12. Distinct from
 * {@code GET /elevators/{id}} rather than a fifth negotiated format on
 * the same request/response pair, because a stream is not a
 * representation of one moment; it is a subscription.
 *
 * <p>An unknown elevator id gets a bare 404 here rather than a
 * negotiated {@code Problem} -- opening an event stream and then
 * immediately failing it does not fit this slice's response-then-done
 * shape, and revisiting it is left to whichever later slice first needs
 * a refusal mid-stream (e.g. an elevator deleted while subscribed).
 */
@RestController
public class StreamEventsController {

    private final ElevatorViewProjection projection;
    private final UriResolver uriResolver;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer renderer;

    public StreamEventsController(
            ElevatorViewProjection projection,
            UriResolver uriResolver,
            ElevatorViewUpdates updates,
            ElevatorStateJsonRenderer renderer) {
        this.projection = projection;
        this.uriResolver = uriResolver;
        this.updates = updates;
        this.renderer = renderer;
    }

    @GetMapping(value = "/elevators/{segment}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String segment) {
        ElevatorId id;
        try {
            id = uriResolver.resolve(segment);
        } catch (RuntimeException invalidSegment) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ElevatorView view = projection.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return updates.subscribe(id, renderer.render(asRepresentation(view)));
    }

    private Representation asRepresentation(ElevatorView view) {
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
}
