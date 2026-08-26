package no.javazone.elevator.feature.streamevents;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.render.HtmlRenderer;
import no.javazone.elevator.shared.security.Principal;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.UriResolver;
import org.springframework.stereotype.Component;
import starfederation.datastar.adapters.response.HttpServletResponseAdapter;
import starfederation.datastar.enums.ElementPatchMode;
import starfederation.datastar.events.PatchElements;
import starfederation.datastar.utils.ServerSentEventGenerator;

/**
 * Fans a per-elevator update out to every open SSE connection for it,
 * each rendered with *that subscriber's own* {@link Principal} -- a
 * technician's own forms must not vanish from their screen the moment
 * another rider's action (or a scheduled event) pushes a patch, any
 * more than a fresh {@code GET} would ever answer a technician with a
 * rider's affordances. See {@code docs/architecture.md}'s "Key-switch
 * and authorization" section: authority is computed per caller
 * everywhere else, and a live push is no exception.
 *
 * <p>Holds the servlet {@link AsyncContext} for each subscriber directly
 * rather than Spring's {@code SseEmitter}: the Datastar Java SDK writes
 * to the raw {@link HttpServletResponse} itself (see
 * {@link StreamEventsController}), so the subscriber's lifetime is a
 * servlet concern here, not a Spring MVC one.
 */
@Component
public class ElevatorViewUpdates {

    private final AffordanceCatalog affordanceCatalog;
    private final UriResolver uriResolver;
    private final HtmlRenderer htmlRenderer;
    private final ElevatorProperties properties;

    private record Subscriber(AsyncContext context, ServerSentEventGenerator events, Principal principal) {
    }

    private final Map<Long, List<Subscriber>> subscribers = new ConcurrentHashMap<>();

    public ElevatorViewUpdates(
            AffordanceCatalog affordanceCatalog, UriResolver uriResolver, HtmlRenderer htmlRenderer,
            ElevatorProperties properties) {
        this.affordanceCatalog = affordanceCatalog;
        this.uriResolver = uriResolver;
        this.htmlRenderer = htmlRenderer;
        this.properties = properties;
    }

    /**
     * Opens the async response and starts an SSE stream for {@code id},
     * sending {@code initialView} rendered for {@code principal}
     * immediately and every subsequent {@link #publish} (rendered fresh
     * for this same principal) until the client disconnects.
     */
    public void subscribe(
            ElevatorId id, HttpServletRequest request, HttpServletResponse response,
            ElevatorView initialView, Principal principal) {
        AsyncContext context = request.startAsync();
        context.setTimeout(0L); // no timeout: the client owns the lifetime

        ServerSentEventGenerator events;
        try {
            events = new ServerSentEventGenerator(new HttpServletResponseAdapter(response));
        } catch (Exception failedToInitialize) {
            context.complete();
            throw new IllegalStateException(
                    "Failed to open the SSE stream for elevator " + id.value(), failedToInitialize);
        }

        Subscriber subscriber = new Subscriber(context, events, principal);
        List<Subscriber> forElevator =
                subscribers.computeIfAbsent(id.value(), key -> new CopyOnWriteArrayList<>());
        forElevator.add(subscriber);

        context.addListener(new RemoveOnFinish(() -> forElevator.remove(subscriber)));
        send(subscriber, id, initialView);
    }

    /** Pushes {@code view}, freshly rendered for each open subscriber's
     * own principal, to every open subscriber for {@code id}. */
    public void publish(ElevatorId id, ElevatorView view) {
        for (Subscriber subscriber : subscribers.getOrDefault(id.value(), List.of())) {
            send(subscriber, id, view);
        }
    }

    private void send(Subscriber subscriber, ElevatorId id, ElevatorView view) {
        String segment = uriResolver.segmentFor(id);
        Representation representation = ElevatorRepresentations.representation(
                segment, view, affordanceCatalog, subscriber.principal(), properties);
        String fragment = htmlRenderer.contentFragment(representation);
        try {
            subscriber.events().send(PatchElements.builder()
                    .selector("#" + ElevatorRepresentations.CONTENT_WRAPPER_ID)
                    .mode(ElementPatchMode.Outer)
                    .data(fragment)
                    .build());
        } catch (RuntimeException disconnected) {
            subscriber.context().complete();
        }
    }

    /** Removes a finished/errored/timed-out subscriber from the fan-out list;
     * a plain lambda can't implement {@link AsyncListener} directly since it
     * declares four methods. */
    private record RemoveOnFinish(Runnable remove) implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) {
            remove.run();
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            remove.run();
        }

        @Override
        public void onError(AsyncEvent event) {
            remove.run();
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
        }
    }
}
