package no.javazone.elevator.feature.opendoors;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import org.junit.jupiter.api.Test;

class OpenDoorsAffordanceContributorTest {

    private final OpenDoorsAffordanceContributor contributor = new OpenDoorsAffordanceContributor();

    @Test
    void presentWhenIdle() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false)))
                .extracting(a -> a.rel())
                .containsExactly("open-doors");
    }

    @Test
    void presentWhileClosing() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsClosing", false)))
                .isNotEmpty();
    }

    @Test
    void absentWhileMoving() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "movingUp", false)))
                .isEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "movingDown", false)))
                .isEmpty();
    }

    @Test
    void absentWhenOutOfServiceOrRecall() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "outOfService", false)))
                .isEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "emergencyRecall", false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
