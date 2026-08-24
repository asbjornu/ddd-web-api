package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestQueueTest {

    @Test
    void startsEmpty() {
        assertThat(RequestQueue.empty().pendingLandingCalls()).isEmpty();
    }

    @Test
    void addsALandingCall() {
        RequestQueue queue = RequestQueue.empty();
        LandingCall call = new LandingCall(new Floor(3), Direction.UP);

        boolean added = queue.addLanding(call);

        assertThat(added).isTrue();
        assertThat(queue.pendingLandingCalls()).containsExactly(call);
    }

    @Test
    void twoRidersPressingTheSameLandingButtonIsOneCall() {
        RequestQueue queue = RequestQueue.empty();
        LandingCall call = new LandingCall(new Floor(3), Direction.UP);
        queue.addLanding(call);

        boolean addedAgain = queue.addLanding(new LandingCall(new Floor(3), Direction.UP));

        assertThat(addedAgain).isFalse();
        assertThat(queue.pendingLandingCalls()).hasSize(1);
    }

    @Test
    void twoRidersPressingDifferentFloorsIsTwoCalls() {
        RequestQueue queue = RequestQueue.empty();
        queue.addLanding(new LandingCall(new Floor(5), Direction.UP));
        queue.addLanding(new LandingCall(new Floor(7), Direction.DOWN));

        assertThat(queue.pendingLandingCalls()).hasSize(2);
    }

    @Test
    void restoresPendingCallsFromPersistence() {
        LandingCall call = new LandingCall(new Floor(2), Direction.DOWN);
        RequestQueue queue = RequestQueue.of(java.util.List.of(call), java.util.List.of());

        assertThat(queue.pendingLandingCalls()).containsExactly(call);
    }
}
