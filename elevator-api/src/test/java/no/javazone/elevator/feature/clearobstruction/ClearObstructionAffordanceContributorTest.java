package no.javazone.elevator.feature.clearobstruction;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import org.junit.jupiter.api.Test;

class ClearObstructionAffordanceContributorTest {

    private final ClearObstructionAffordanceContributor contributor =
            new ClearObstructionAffordanceContributor();

    @Test
    void presentWhenObstructed() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen", true)))
                .extracting(a -> a.rel())
                .containsExactly("clear-obstruction");
    }

    @Test
    void absentWhenNotObstructed() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "doorsOpen", false)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
