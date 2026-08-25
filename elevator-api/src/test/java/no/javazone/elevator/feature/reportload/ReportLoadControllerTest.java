package no.javazone.elevator.feature.reportload;

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
class ReportLoadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorAggregateStore store;

    private void seedDoorsOpen() {
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.DoorsOpen(),
                new Doors(Doors.DoorPosition.OPEN, false),
                new Load(0, 800),
                RequestQueue.empty()));
    }

    @Test
    void reportingLoadWhileDoorsOpenSucceeds() throws Exception {
        seedDoorsOpen();

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ReportLoad\", \"weightKg\": 500}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"weightKg\" : 500")));
    }

    @Test
    void reportingLoadWhileDoorsClosedIsAConflict() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ReportLoad\", \"weightKg\": 500}"))
                .andExpect(status().isConflict());
    }

    @Test
    void missingWeightIsARefusedRequest() throws Exception {
        seedDoorsOpen();

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ReportLoad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ReportLoad\", \"weightKg\": 500}"))
                .andExpect(status().isNotFound());
    }

    // Overloading here also proves select-floor now disappears once the
    // read model reflects it -- see SelectFloorControllerTest for the
    // "typed problem" half of this slice's named test.
    @Test
    void reportingAnOverloadRemovesTheSelectFloorAffordance() throws Exception {
        seedDoorsOpen();

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ReportLoad\", \"weightKg\": 900}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("select-floor"))));
    }
}
