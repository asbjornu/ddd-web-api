package no.javazone.elevator.feature.obstructdoors;

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
class ObstructDoorsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorAggregateStore store;

    private void seedDoorsClosing() {
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.DoorsClosing(),
                new Doors(Doors.DoorPosition.CLOSING, false),
                new Load(0, 800),
                RequestQueue.empty()));
    }

    // "Obstruction during closing re-opens" -- the test named in this
    // slice's roadmap entry. @DirtiesContext: a successful obstruction
    // re-opens the doors, which schedules a real auto-close timer --
    // see MovementSchedulerIntegrationTest's Javadoc.
    @Test
    @DirtiesContext
    void obstructingClosingDoorsReopensThem() throws Exception {
        seedDoorsClosing();

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ObstructDoors\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\" : \"doorsOpen\"")))
                .andExpect(content().string(containsString("\"obstructed\" : true")));
    }

    @Test
    void obstructingWhenNotClosingIsAConflict() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ObstructDoors\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ObstructDoors\"}"))
                .andExpect(status().isNotFound());
    }
}
