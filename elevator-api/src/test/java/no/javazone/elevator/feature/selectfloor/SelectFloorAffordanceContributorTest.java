package no.javazone.elevator.feature.selectfloor;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import org.junit.jupiter.api.Test;

class SelectFloorAffordanceContributorTest {

    private final SelectFloorAffordanceContributor contributor =
            new SelectFloorAffordanceContributor();

    @Test
    void presentWhenIdle() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle")))
                .extracting(a -> a.rel())
                .containsExactly("select-floor");
    }

    @Test
    void presentWhileMoving() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "movingUp")))
                .isNotEmpty();
    }

    @Test
    void absentWhenOutOfService() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "outOfService")))
                .isEmpty();
    }

    @Test
    void absentWhenEmergencyRecall() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "emergencyRecall")))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
