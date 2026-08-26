package no.javazone.elevator.feature.selectfloor;

import static org.assertj.core.api.Assertions.assertThat;

import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import org.junit.jupiter.api.Test;

class SelectFloorAffordanceContributorTest {

    private final SelectFloorAffordanceContributor contributor =
            new SelectFloorAffordanceContributor(new no.javazone.elevator.config.ElevatorProperties(9, 1, 2, 6, 800));

    @Test
    void presentWhenIdle() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .extracting(a -> a.rel())
                .containsExactly("select-floor");
    }

    @Test
    void offersEveryFloorAsAnOption() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, false)))
                .singleElement()
                .satisfies(affordance -> assertThat(affordance.fields())
                        .filteredOn(field -> "floor".equals(field.name()))
                        .singleElement()
                        .satisfies(field -> assertThat(field.options())
                                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9")));
    }

    @Test
    void presentWhileMoving() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "movingUp", false, false)))
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

    // "select-floor absent when overloaded -- no 409 needed" -- the
    // test named in this slice's (Overload) roadmap entry.
    @Test
    void absentWhenOverloaded() {
        assertThat(contributor.contribute(AffordanceContext.forElevator("1", "idle", false, true)))
                .isEmpty();
    }

    @Test
    void absentAtTheEntryPoint() {
        assertThat(contributor.contribute(AffordanceContext.root())).isEmpty();
    }
}
