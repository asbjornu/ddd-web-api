package no.javazone.elevator.feature.closedoors;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import org.junit.jupiter.api.Test;

class CloseDoorsAffordanceContributorTest {

    private final CloseDoorsAffordanceContributor contributor =
            new CloseDoorsAffordanceContributor();

    @Test
    void presentWhenDoorsOpen() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen", false)))
                .extracting(a -> a.rel())
                .containsExactly("close-doors");
    }

    // Present even while obstructed: the command itself is what
    // refuses -- see this slice's commit message.
    @Test
    void presentWhenDoorsOpenAndObstructed() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen", true)))
                .isNotEmpty();
    }

    @Test
    void absentWhenNotOpen() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false)))
                .isEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsClosing", false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
