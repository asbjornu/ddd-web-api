package no.javazone.elevator.feature.streamevents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import no.javazone.elevator.TestJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
class StreamEventsControllerTest {

    @Autowired
    private StreamEventsController controller;

    @Test
    void subscribingToTheSeededElevatorReturnsAnEmitter() {
        assertThat(controller.stream("1")).isNotNull();
    }

    @Test
    void anUnknownElevatorIsRefusedWithNotFound() {
        assertThatThrownBy(() -> controller.stream("999"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void anUnparseableSegmentIsRefusedWithNotFound() {
        assertThatThrownBy(() -> controller.stream("not-a-number"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
