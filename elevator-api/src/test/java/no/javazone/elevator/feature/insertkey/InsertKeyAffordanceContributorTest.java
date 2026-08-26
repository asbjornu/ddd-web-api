package no.javazone.elevator.feature.insertkey;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.security.Principal;
import org.junit.jupiter.api.Test;

class InsertKeyAffordanceContributorTest {

    private final InsertKeyAffordanceContributor contributor = new InsertKeyAffordanceContributor();

    @Test
    void presentForAnAnonymousCaller() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .extracting(a -> a.rel())
                .containsExactly("insert-key");
    }

    @Test
    void pointsAtTheSessionEndpointWithASecretField() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .singleElement()
                .satisfies(affordance -> {
                    assertThat(affordance.href()).isEqualTo("/elevators/1/key-switch/session");
                    assertThat(affordance.method()).isEqualTo("POST");
                    assertThat(affordance.fields()).extracting(f -> f.name()).containsExactly("secret");
                });
    }

    @Test
    void absentForACallerAlreadyHoldingAScope() {
        Principal technician = new Principal(java.util.Set.of("elevator:maintenance"));
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "idle", false, false, technician)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }

    @Test
    void absentForAnyoneDuringAnEmergencyRecall() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "emergencyRecall", false, false)))
                .isEmpty();
    }
}
