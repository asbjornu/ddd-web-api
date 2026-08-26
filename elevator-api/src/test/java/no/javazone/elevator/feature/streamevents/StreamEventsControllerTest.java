package no.javazone.elevator.feature.streamevents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import no.javazone.elevator.TestJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
class StreamEventsControllerTest {

    @Autowired
    private StreamEventsController controller;

    @Test
    void subscribingToTheSeededElevatorSendsTheInitialPatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.stream("1", request, response);

        assertThat(response.getContentAsString()).contains("datastar-patch-elements");
    }

    @Test
    void anUnknownElevatorIsRefusedWithNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.stream("999", request, response))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void anUnparseableSegmentIsRefusedWithNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAsyncSupported(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.stream("not-a-number", request, response))
                .isInstanceOf(ResponseStatusException.class);
    }
}
