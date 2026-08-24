package no.javazone.elevator.feature.selectfloor;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class SelectFloorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorAggregateStore store;

    // @DirtiesContext: floor 5 is 4 floors from the seeded elevator, so
    // this really dispatches and schedules a background arrival -- see
    // MovementSchedulerIntegrationTest's Javadoc.
    @Test
    @DirtiesContext
    void selectingAFloorFromIdleDispatchesTheCar() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"SelectFloor\", \"floor\": 5}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.siren+json"))
                .andExpect(content().string(containsString("\"state\" : \"movingUp\"")))
                .andExpect(content().string(containsString("select-floor")));
    }

    @Test
    void missingFieldsAreARefusedRequest() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"SelectFloor\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"SelectFloor\", \"floor\": 3}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void selectingAFloorOnAnOutOfServiceElevatorIsAConflict() throws Exception {
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.OutOfService(),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty()));

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"SelectFloor\", \"floor\": 3}"))
                .andExpect(status().isConflict());
    }

    @Test
    void selectingAnOverloadedCarsFloorIsAConflict() throws Exception {
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.Idle(),
                Doors.closed(),
                new Load(900, 800),
                RequestQueue.empty()));

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"SelectFloor\", \"floor\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("overloaded")));
    }
}
