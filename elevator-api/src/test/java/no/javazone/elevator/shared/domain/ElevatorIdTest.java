package no.javazone.elevator.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ElevatorIdTest {

    @Test
    void carriesItsValue() {
        assertThat(new ElevatorId(1).value()).isEqualTo(1);
    }

    @Test
    void rejectsZeroAndNegativeValues() {
        assertThatThrownBy(() -> new ElevatorId(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ElevatorId(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
