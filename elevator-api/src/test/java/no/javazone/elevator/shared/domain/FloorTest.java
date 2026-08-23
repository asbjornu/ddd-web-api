package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FloorTest {

    @Test
    void mostFloorsAreNotTheRecallFloor() {
        assertThat(new Floor(3).isRecallFloor()).isFalse();
    }

    @Test
    void theRecallFloorSaysSo() {
        assertThat(new Floor(1, true).isRecallFloor()).isTrue();
    }

    @Test
    void rejectsLevelsBelowOne() {
        assertThatThrownBy(() -> new Floor(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
