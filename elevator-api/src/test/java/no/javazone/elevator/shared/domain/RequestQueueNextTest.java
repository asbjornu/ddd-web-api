package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The SCAN/LOOK algorithm itself, independent of the aggregate --
 * "Direction-committed ordering across pending calls", the test named
 * in docs/architecture.md's slice 3 roadmap entry.
 */
class RequestQueueNextTest {

    @Test
    void nearestFloorWhenIdle() {
        RequestQueue queue = RequestQueue.empty();
        queue.addCar(new CarCall(new Floor(5)));
        queue.addCar(new CarCall(new Floor(8)));

        assertThat(queue.next(new Floor(6), Direction.NONE)).contains(new Floor(5));
    }

    @Test
    void continuesUpwardsPastNearerPendingFloorsBeforeReversing() {
        RequestQueue queue = RequestQueue.empty();
        queue.addCar(new CarCall(new Floor(3)));
        queue.addCar(new CarCall(new Floor(7)));

        // Travelling up from 5: 7 is ahead, 3 is behind. Direction
        // committed to UP serves 7 first.
        assertThat(queue.next(new Floor(5), Direction.UP)).contains(new Floor(7));
    }

    @Test
    void reversesOnlyWhenNothingRemainsAhead() {
        RequestQueue queue = RequestQueue.empty();
        queue.addCar(new CarCall(new Floor(3)));

        assertThat(queue.next(new Floor(5), Direction.UP)).contains(new Floor(3));
    }

    @Test
    void travellingDownServesTheNearestFloorBelowFirst() {
        RequestQueue queue = RequestQueue.empty();
        queue.addLanding(new LandingCall(new Floor(2), Direction.UP));
        queue.addLanding(new LandingCall(new Floor(6), Direction.DOWN));

        assertThat(queue.next(new Floor(4), Direction.DOWN)).contains(new Floor(2));
    }

    @Test
    void emptyQueueHasNoNextFloor() {
        assertThat(RequestQueue.empty().next(new Floor(1), Direction.NONE)).isEmpty();
    }

    @Test
    void ignoresTheCurrentFloorItself() {
        RequestQueue queue = RequestQueue.empty();
        queue.addCar(new CarCall(new Floor(4)));

        assertThat(queue.next(new Floor(4), Direction.NONE)).isEmpty();
    }

    @Test
    void clearAtRemovesBothCallTypesForThatFloor() {
        RequestQueue queue = RequestQueue.empty();
        queue.addLanding(new LandingCall(new Floor(4), Direction.UP));
        queue.addCar(new CarCall(new Floor(4)));

        queue.clearAt(new Floor(4));

        assertThat(queue.pendingLandingCalls()).isEmpty();
        assertThat(queue.pendingCarCalls()).isEmpty();
    }

    @Test
    void clearDiscardsEverything() {
        RequestQueue queue = RequestQueue.empty();
        queue.addLanding(new LandingCall(new Floor(2), Direction.UP));
        queue.addCar(new CarCall(new Floor(9)));

        queue.clear();

        assertThat(queue.pendingLandingCalls()).isEmpty();
        assertThat(queue.pendingCarCalls()).isEmpty();
    }

    @Test
    void aSecondCarCallAtTheSameFloorIsIgnored() {
        RequestQueue queue = RequestQueue.empty();
        assertThat(queue.addCar(new CarCall(new Floor(5)))).isTrue();
        assertThat(queue.addCar(new CarCall(new Floor(5)))).isFalse();
        assertThat(queue.pendingCarCalls()).isEqualTo(List.of(new CarCall(new Floor(5))));
    }
}
