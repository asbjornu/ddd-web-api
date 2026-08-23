package no.javazone.elevator.feature.streamevents;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import no.javazone.elevator.shared.domain.ElevatorId;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fans a per-elevator update out to every open SSE connection for it.
 * Replaces polling: the server pushes because it knows when something
 * happened, rather than the client asking every 1.5 seconds -- see
 * {@code docs/plan.html} &sect;12's "Datastar & SSE".
 *
 * <p>Nothing calls {@link #publish} yet: no command has moved onto the
 * new aggregate to trigger one. From slice 2 onward, a command handler
 * calls it after {@link no.javazone.elevator.feature.viewstatus.ElevatorViewProjection}
 * has folded the resulting event in, so a subscriber hears about a
 * change in the same request that caused it.
 */
@Component
public class ElevatorViewUpdates {

    private final Map<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(ElevatorId id, String initialEventJson) {
        SseEmitter emitter = new SseEmitter(0L /* no timeout: the client owns the lifetime */);
        List<SseEmitter> emitters =
                subscribers.computeIfAbsent(id.value(), key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(throwable -> remove.run());

        send(emitter, initialEventJson);
        return emitter;
    }

    /** Pushes {@code eventJson} to every open subscriber for {@code id}. */
    public void publish(ElevatorId id, String eventJson) {
        for (SseEmitter emitter : subscribers.getOrDefault(id.value(), List.of())) {
            send(emitter, eventJson);
        }
    }

    private void send(SseEmitter emitter, String json) {
        try {
            emitter.send(SseEmitter.event().name("elevator-updated").data(json));
        } catch (IOException | IllegalStateException disconnected) {
            emitter.completeWithError(disconnected);
        }
    }
}
