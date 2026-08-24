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
                contributor.contribute(AffordanceContext.forElevator("1", "idle", false));

        assertThat(affordances).extracting(Affordance::rel).containsExactly("call-elevator");
    }

    @Test
    void presentWhenDoorsOpenOrClosing() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen", false)))
                .isNotEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsClosing", false)))
                .isNotEmpty();
    }

    @Test
    void absentWhenOutOfService() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "outOfService", false)))
                .isEmpty();
    }

    @Test
    void absentWhenEmergencyRecall() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "emergencyRecall", false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
