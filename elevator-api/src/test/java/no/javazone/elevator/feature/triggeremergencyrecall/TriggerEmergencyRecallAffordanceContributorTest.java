package no.javazone.elevator.feature.triggeremergencyrecall;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.security.Principal;
import org.junit.jupiter.api.Test;

class TriggerEmergencyRecallAffordanceContributorTest {

    private final TriggerEmergencyRecallAffordanceContributor contributor =
            new TriggerEmergencyRecallAffordanceContributor();

    private static final Principal TECHNICIAN = new Principal(Set.of("elevator:recall"));

    @Test
    void presentForATechnicianWhenIdle() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "idle", false, false, TECHNICIAN)))
                .extracting(a -> a.rel())
                .containsExactly("trigger-emergency-recall");
    }

    @Test
    void presentEvenWhileAlreadyInMaintenance() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "outOfService", false, false, TECHNICIAN)))
                .extracting(a -> a.rel())
                .containsExactly("trigger-emergency-recall");
    }

    @Test
    void absentWithoutTheScope() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .isEmpty();
    }

    @Test
    void absentDuringAnAlreadyOngoingRecall() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "emergencyRecall", false, false, TECHNICIAN)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root(TECHNICIAN))).isEmpty();
    }
}
