package no.javazone.elevator.feature.entermaintenance;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.security.Principal;
import org.junit.jupiter.api.Test;

class EnterMaintenanceAffordanceContributorTest {

    private final EnterMaintenanceAffordanceContributor contributor =
            new EnterMaintenanceAffordanceContributor();

    private static final Principal TECHNICIAN = new Principal(java.util.Set.of("elevator:maintenance"));

    @Test
    void presentForATechnicianWhenIdle() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "idle", false, false, TECHNICIAN)))
                .extracting(a -> a.rel())
                .containsExactly("enter-maintenance");
    }

    @Test
    void absentWithoutTheScope() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .isEmpty();
    }

    @Test
    void absentWhenAlreadyOutOfService() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "outOfService", false, false, TECHNICIAN)))
                .isEmpty();
    }

    @Test
    void absentDuringEmergencyRecall() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "emergencyRecall", false, false, TECHNICIAN)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root(TECHNICIAN))).isEmpty();
    }
}
