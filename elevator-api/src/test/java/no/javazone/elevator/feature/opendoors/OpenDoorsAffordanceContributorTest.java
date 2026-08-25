package no.javazone.elevator.feature.opendoors;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import org.junit.jupiter.api.Test;

class OpenDoorsAffordanceContributorTest {

    private final OpenDoorsAffordanceContributor contributor = new OpenDoorsAffordanceContributor();

    @Test
    void presentWhenIdle() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .extracting(a -> a.rel())
                .containsExactly("open-doors");
    }

    @Test
    void presentWhileClosing() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsClosing", false, false)))
                .isNotEmpty();
    }

    @Test
    void absentWhileMoving() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "movingUp", false, false)))
                .isEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "movingDown", false, false)))
                .isEmpty();
    }

    @Test
    void absentWhenOutOfServiceOrRecall() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "outOfService", false, false)))
                .isEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "emergencyRecall", false, false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
