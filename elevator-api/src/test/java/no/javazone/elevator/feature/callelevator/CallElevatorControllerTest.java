package no.javazone.elevator.feature.callelevator;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import no.javazone.elevator.TestJwtDecoderConfig;
import no.javazone.elevator.shared.domain.Doors;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.ElevatorState;
import no.javazone.elevator.shared.domain.Floor;
import no.javazone.elevator.shared.domain.Load;
import no.javazone.elevator.shared.domain.RequestQueue;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class CallElevatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorAggregateStore store;

    @Test
    void callingTheSeededElevatorSucceeds() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CallElevator\", \"floor\": 5, \"direction\": \"up\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.siren+json"))
                .andExpect(content().string(containsString("call-elevator")));
    }

    @Test
    void aCallIsPersistedOnTheAggregate() throws Exception {
        mockMvc.perform(post("/elevators/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"CallElevator\", \"floor\": 7, \"direction\": \"down\"}"));

        Elevator elevator = store.find(new ElevatorId(1)).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(elevator.queue().pendingLandingCalls())
                .anyMatch(call -> call.floor().level() == 7);
    }

    @Test
    void missingFieldsAreARefusedRequest() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CallElevator\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void anInvalidDirectionIsARefusedRequest() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CallElevator\", \"floor\": 3, \"direction\": \"sideways\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CallElevator\", \"floor\": 3, \"direction\": \"up\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void callingAnOutOfServiceElevatorIsAConflict() throws Exception {
        ElevatorId id = new ElevatorId(1);
        Elevator outOfService = Elevator.restore(
                id,
                new Floor(1),
                new ElevatorState.OutOfService(),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty());
        store.save(outOfService);

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CallElevator\", \"floor\": 3, \"direction\": \"up\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("out of service")));
    }
}
