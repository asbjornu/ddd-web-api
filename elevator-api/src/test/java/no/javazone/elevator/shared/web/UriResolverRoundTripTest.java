package no.javazone.elevator.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.random.RandomGenerator;
import java.util.stream.LongStream;
import no.javazone.elevator.shared.domain.ElevatorId;
import org.junit.jupiter.api.Test;

/**
 * Both resolvers must round-trip identically, and an opaque segment must
 * not merely be the id restated in another radix -- see
 * {@code docs/plan.html} &sect;8's "The test that settles it": nothing
 * outside {@code shared.web} may behave differently depending on which
 * style is active.
 */
class UriResolverRoundTripTest {

    @Test
    void readableRoundTrips() {
        UriResolver resolver = new ReadableUriResolver();
        for (long value : sampleIds()) {
            ElevatorId id = new ElevatorId(value);
            assertThat(resolver.resolve(resolver.segmentFor(id))).isEqualTo(id);
        }
    }

    @Test
    void opaqueRoundTrips() {
        UriResolver resolver = new OpaqueUriResolver();
        for (long value : sampleIds()) {
            ElevatorId id = new ElevatorId(value);
            assertThat(resolver.resolve(resolver.segmentFor(id))).isEqualTo(id);
        }
    }

    @Test
    void opaqueSegmentIsNotTheRawIdInDisguise() {
        UriResolver readable = new ReadableUriResolver();
        UriResolver opaque = new OpaqueUriResolver();
        ElevatorId id = new ElevatorId(1);

        assertThat(opaque.segmentFor(id)).isNotEqualTo(readable.segmentFor(id));
    }

    @Test
    void consecutiveIdsDoNotProduceConsecutiveOpaqueSegments() {
        UriResolver opaque = new OpaqueUriResolver();
        long first = Long.parseLong(opaque.segmentFor(new ElevatorId(1)), 36);
        long second = Long.parseLong(opaque.segmentFor(new ElevatorId(2)), 36);

        // Not a proof of unguessability -- see OpaqueUriResolver's own
        // documentation -- only that /elevators/2 is not "one guess
        // away" from /elevators/1 the way a sequential id would be.
        assertThat(second).isNotEqualTo(first + 1);
    }

    private long[] sampleIds() {
        RandomGenerator random = RandomGenerator.getDefault();
        return LongStream.concat(
                        LongStream.of(1, 2, 42, Integer.MAX_VALUE),
                        random.longs(20, 1, 1_000_000_000L))
                .toArray();
    }
}
