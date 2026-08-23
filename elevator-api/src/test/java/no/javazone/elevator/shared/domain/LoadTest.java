package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LoadTest {

    @Test
    void withinCapacityIsNotOverloaded() {
        assertThat(new Load(799, 800).isOverloaded()).isFalse();
        assertThat(new Load(800, 800).isOverloaded()).isFalse();
    }

    @Test
    void overCapacityIsOverloaded() {
        assertThat(new Load(801, 800).isOverloaded()).isTrue();
    }

    @Test
    void rejectsNegativeWeight() {
        assertThatThrownBy(() -> new Load(-1, 800))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThatThrownBy(() -> new Load(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
