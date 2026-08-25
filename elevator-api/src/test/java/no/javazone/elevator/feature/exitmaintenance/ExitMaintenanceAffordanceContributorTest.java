package no.javazone.elevator.feature.exitmaintenance;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.security.Principal;
import org.junit.jupiter.api.Test;

class ExitMaintenanceAffordanceContributorTest {

    private final ExitMaintenanceAffordanceContributor contributor =
            new ExitMaintenanceAffordanceContributor();

    private static final Principal TECHNICIAN = new Principal(java.util.Set.of("elevator:maintenance"));

    @Test
    void presentForATechnicianWhenOutOfService() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "outOfService", false, false, TECHNICIAN)))
                .extracting(a -> a.rel())
                .containsExactly("exit-maintenance");
    }

    @Test
    void absentWithoutTheScope() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "outOfService", false, false)))
                .isEmpty();
    }

    @Test
    void absentWhenNotOutOfService() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "idle", false, false, TECHNICIAN)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root(TECHNICIAN))).isEmpty();
    }
}
