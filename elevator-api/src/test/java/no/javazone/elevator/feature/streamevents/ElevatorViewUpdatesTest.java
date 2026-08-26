package no.javazone.elevator.feature.streamevents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;

import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.render.HtmlRenderer;
import no.javazone.elevator.shared.security.Principal;
import no.javazone.elevator.shared.web.UriResolver;
import no.javazone.elevator.shared.web.ReadableUriResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Real push-on-change behaviour is properly exercised end-to-end once a
 * command or scheduler actually calls {@link #publish} against a real
 * elevator -- see {@code no.javazone.elevator.feature.callelevator.CallElevatorControllerTest}
 * and friends. These tests only pin down that subscribing writes the
 * initial {@code datastar-patch-elements} event rendered for the
 * subscriber's own principal, that publishing without a subscriber does
 * not throw, and that a subscriber receives a later publish too,
 * rendered fresh.
 */
class ElevatorViewUpdatesTest {

    private final UriResolver uriResolver = new ReadableUriResolver();
    private final ElevatorViewUpdates updates =
            new ElevatorViewUpdates(
                    new AffordanceCatalog(java.util.List.of()), uriResolver, new HtmlRenderer(),
                    new ElevatorProperties(9, 1, 2, 6, 800));
    private final ElevatorId id = new ElevatorId(1);

    private ElevatorView view(int currentFloor) {
        return new ElevatorView(1, currentFloor, "idle", "none", "closed", false, 0, 800, null);
    }

    @Test
    void subscribingSendsTheInitialEventWithoutThrowing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatNoException().isThrownBy(
                () -> updates.subscribe(id, request, response, view(1), Principal.ANONYMOUS));

        String content = response.getContentAsString();
        assertThat(content).contains("datastar-patch-elements");
        assertThat(content).contains("elevator-content");
    }

    @Test
    void publishingWithNoSubscribersDoesNothing() {
        assertThatCode(() -> updates.publish(id, view(2))).doesNotThrowAnyException();
    }

    @Test
    void publishingAfterSubscribingReachesTheSubscriber() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        updates.subscribe(id, request, response, view(1), Principal.ANONYMOUS);

        assertThatNoException().isThrownBy(() -> updates.publish(id, view(2)));
        assertThat(response.getContentAsString()).contains("<dd>2</dd>");
    }
}
