package no.javazone.elevator.feature.callelevator;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class CallElevatorAffordanceContributorTest {

    private final CallElevatorAffordanceContributor contributor =
            new CallElevatorAffordanceContributor(new no.javazone.elevator.config.ElevatorProperties(9, 1, 2, 6, 800));

    @Test
    void presentWhenIdle() {
        List<Affordance> affordances =
                contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false));

        assertThat(affordances).extracting(Affordance::rel).containsExactly("call-elevator");
    }

    @Test
    void offersEveryFloorAsAnOption() {
        List<Affordance> affordances =
                contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false));

        assertThat(affordances)
                .singleElement()
                .satisfies(affordance -> assertThat(affordance.fields())
                        .filteredOn(field -> "floor".equals(field.name()))
                        .singleElement()
                        .satisfies(field -> assertThat(field.options())
                                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9")));
    }

    @Test
    void presentWhenDoorsOpenOrClosing() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen", false, false)))
                .isNotEmpty();
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsClosing", false, false)))
                .isNotEmpty();
    }

    @Test
    void absentWhenOutOfService() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "outOfService", false, false)))
                .isEmpty();
    }

    @Test
    void absentWhenEmergencyRecall() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "emergencyRecall", false, false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
