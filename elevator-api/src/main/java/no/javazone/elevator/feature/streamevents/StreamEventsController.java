package no.javazone.elevator.feature.streamevents;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.security.PrincipalResolver;
import no.javazone.elevator.shared.web.UriResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * {@code GET /elevators/{id}/events}: one stream that speaks only when
 * something happened, replacing the 1.5 second poll -- see
 * {@code docs/plan.html} &sect;12. Distinct from
 * {@code GET /elevators/{id}} rather than a fifth negotiated format on
 * the same request/response pair, because a stream is not a
 * representation of one moment; it is a subscription.
 *
 * <p>Resolves this caller's own {@link no.javazone.elevator.shared.security.Principal}
 * exactly once, at subscribe time, from whatever Spring Security (or
 * {@link no.javazone.elevator.shared.security.TechnicianCookieAuthenticationFilter}
 * standing in for it) already validated on this very request -- see
 * {@link ElevatorViewUpdates}'s own Javadoc for why every later patch on
 * this connection is rendered for that same principal, not a shared,
 * anonymous shape.
 *
 * <p>Speaks the Datastar wire format directly against the raw servlet
 * request/response ({@link ElevatorViewUpdates#subscribe}) rather than
 * Spring MVC's {@code SseEmitter}: the Datastar Java SDK owns the
 * response's {@code PrintWriter} itself, so this controller starts the
 * async context and hands the SDK the rest.
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
    private final PrincipalResolver principalResolver;

    public StreamEventsController(
            ElevatorViewProjection projection,
            UriResolver uriResolver,
            ElevatorViewUpdates updates,
            PrincipalResolver principalResolver) {
        this.projection = projection;
        this.uriResolver = uriResolver;
        this.updates = updates;
        this.principalResolver = principalResolver;
    }

    @GetMapping(value = "/elevators/{segment}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void stream(
            @PathVariable String segment, HttpServletRequest request, HttpServletResponse response) {
        // Asserted before anything else touches the response: a browser's
        // own EventSource aborts the connection outright if the
        // Content-Type header's charset is not UTF-8 (Tomcat's own
        // response default is ISO-8859-1 until something says otherwise),
        // and this must happen before the Datastar SDK's own writer is
        // obtained in ElevatorViewUpdates#subscribe.
        response.setCharacterEncoding("UTF-8");
        ElevatorId id;
        try {
            id = uriResolver.resolve(segment);
        } catch (RuntimeException invalidSegment) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ElevatorView view = projection.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        updates.subscribe(id, request, response, view, principalResolver.resolve());
    }
}
