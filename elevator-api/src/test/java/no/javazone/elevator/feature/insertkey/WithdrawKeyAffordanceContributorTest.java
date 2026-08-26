package no.javazone.elevator.feature.insertkey;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.security.Principal;
import org.junit.jupiter.api.Test;

class WithdrawKeyAffordanceContributorTest {

    private final WithdrawKeyAffordanceContributor contributor = new WithdrawKeyAffordanceContributor();
    private final Principal technician = new Principal(java.util.Set.of("elevator:maintenance"));

    @Test
    void presentForACallerHoldingAScope() {
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "idle", false, false, technician)))
                .singleElement()
                .satisfies(affordance -> {
                    assertThat(affordance.rel()).isEqualTo("withdraw-key");
                    assertThat(affordance.method()).isEqualTo("DELETE");
                    assertThat(affordance.href()).isEqualTo("/elevators/1/key-switch/session");
                });
    }

    @Test
    void absentForAnAnonymousCaller() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
