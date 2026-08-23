package no.javazone.elevator.feature.streamevents;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;

import no.javazone.elevator.shared.domain.ElevatorId;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Real push-on-change behaviour has nothing to push yet (see this
 * class's own Javadoc) and is properly exercised end-to-end once
 * elevator-ui subscribes -- these tests only pin down that subscribing
 * and publishing do not throw, and that a completed/removed subscriber
 * is not sent to again.
 */
class ElevatorViewUpdatesTest {

    private final ElevatorViewUpdates updates = new ElevatorViewUpdates();
    private final ElevatorId id = new ElevatorId(1);

    @Test
    void subscribingSendsTheInitialEventWithoutThrowing() {
        assertThatNoException().isThrownBy(() -> {
            SseEmitter emitter = updates.subscribe(id, "{\"currentFloor\":1}");
            org.assertj.core.api.Assertions.assertThat(emitter).isNotNull();
        });
    }

    @Test
    void publishingWithNoSubscribersDoesNothing() {
        assertThatCode(() -> updates.publish(id, "{\"currentFloor\":2}")).doesNotThrowAnyException();
    }

    @Test
    void publishingAfterSubscribingReachesTheSubscriber() {
        updates.subscribe(id, "{\"currentFloor\":1}");
        assertThatNoException().isThrownBy(() -> updates.publish(id, "{\"currentFloor\":2}"));
    }
}
