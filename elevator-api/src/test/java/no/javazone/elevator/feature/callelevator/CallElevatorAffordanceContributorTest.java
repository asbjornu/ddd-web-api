package no.javazone.elevator.feature.callelevator;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class CallElevatorAffordanceContributorTest {

    private final CallElevatorAffordanceContributor contributor =
            new CallElevatorAffordanceContributor();

    @Test
    void presentWhenIdle() {
        List<Affordance> affordances =
                contributor.contribute(AffordanceContext.forElevator("1", "idle"));

        assertThat(affordances).extracting(Affordance::rel).containsExactly("call-elevator");
    }

    @Test
    void presentWhenDoorsOpenOrClosing() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen")))
                .isNotEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsClosing")))
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
