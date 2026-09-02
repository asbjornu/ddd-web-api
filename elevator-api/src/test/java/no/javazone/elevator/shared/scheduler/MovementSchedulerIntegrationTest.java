package no.javazone.elevator.shared.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import no.javazone.elevator.TestJwtDecoderConfig;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.Floor;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "Scheduler fires at the computed instant" -- the test named in
 * docs/architecture.md's slice 3 roadmap entry. Deliberately not
 * {@code @Transactional}: {@link MovementScheduler} runs the arrival on
 * a separate thread and connection, which would not see this test's own
 * writes if they were left uncommitted in an open transaction -- so
 * this test commits for real, and cleans up for real afterwards
 * instead.
 */
@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
class MovementSchedulerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorAggregateStore store;

    @Autowired
    private ElevatorViewProjection projection;

    @AfterEach
    void reseedTheElevator() {
        Elevator seeded = Elevator.seed(new ElevatorId(1), new Floor(1), 800);
        store.save(seeded);
        projection.syncFrom(seeded);
    }

    @Test
    void theElevatorActuallyArrivesAtTheComputedInstant() throws Exception {
        // Default config travels 2 s/floor; floor 2 is one floor away.
        mockMvc.perform(post("/elevators/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"SelectFloor\", \"floor\": 2}"));

        Thread.sleep(2600);

        mockMvc.perform(get("/elevators/1").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"currentFloor\" : 2")))
                .andExpect(content().string(containsString("\"state\" : \"doorsOpen\"")));

        assertThat(store.find(new ElevatorId(1)).orElseThrow().queue().pendingCarCalls())
                .isEmpty();
    }

    @Test
    void positionAdvancesFloorByFloorRatherThanJumpingToTheDestination() throws Exception {
        // Floor 4 is three floors away -- long enough a trip to observe
        // an intermediate floor's own scheduled callback fire on its
        // own, before the final one that actually arrives.
        mockMvc.perform(post("/elevators/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"SelectFloor\", \"floor\": 4}"));

        // One floor (2 s) in: still travelling, but already past floor 1.
        Thread.sleep(2600);
        mockMvc.perform(get("/elevators/1").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"currentFloor\" : 2")))
                .andExpect(content().string(containsString("\"state\" : \"movingUp\"")));

        // Two floors (4 s) in: further along still, still travelling.
        Thread.sleep(2000);
        mockMvc.perform(get("/elevators/1").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"currentFloor\" : 3")))
                .andExpect(content().string(containsString("\"state\" : \"movingUp\"")));

        // Three floors (6 s) in: arrived, doors open.
        Thread.sleep(2600);
        mockMvc.perform(get("/elevators/1").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"currentFloor\" : 4")))
                .andExpect(content().string(containsString("\"state\" : \"doorsOpen\"")));
    }
}
