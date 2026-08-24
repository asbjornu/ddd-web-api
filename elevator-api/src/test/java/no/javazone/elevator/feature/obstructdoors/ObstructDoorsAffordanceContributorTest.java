package no.javazone.elevator.feature.obstructdoors;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import org.junit.jupiter.api.Test;

/** "obstruct-doors offered only while closing" -- the test named in this slice's roadmap entry. */
class ObstructDoorsAffordanceContributorTest {

    private final ObstructDoorsAffordanceContributor contributor =
            new ObstructDoorsAffordanceContributor();

    @Test
    void presentOnlyWhileClosing() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsClosing", false)))
                .extracting(a -> a.rel())
                .containsExactly("obstruct-doors");
    }

    @Test
    void absentWhenIdleOrOpenOrMoving() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false)))
                .isEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen", false)))
                .isEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "movingUp", false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
