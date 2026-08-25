package no.javazone.elevator.feature.reportload;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import org.junit.jupiter.api.Test;

class ReportLoadAffordanceContributorTest {

    private final ReportLoadAffordanceContributor contributor =
            new ReportLoadAffordanceContributor();

    @Test
    void presentWhenDoorsOpen() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen", false, false)))
                .extracting(a -> a.rel())
                .containsExactly("report-load");
    }

    @Test
    void absentWhenNotOpen() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .isEmpty();
        assertThat(contributor.contribute(
                        AffordanceContext.forElevator("1", "doorsClosing", false, false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
