package no.javazone.elevator.feature.closedoors;

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
class CloseDoorsControllerTest {

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

    // @DirtiesContext: a successful close schedules a real "finish
    // closing" timer -- see MovementSchedulerIntegrationTest's Javadoc.
    @Test
    @DirtiesContext
    void closingOpenDoorsSucceeds() throws Exception {
        seedDoorsOpen();

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CloseDoors\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\" : \"doorsClosing\"")));
    }

    @Test
    void closingAlreadyClosedDoorsIsAConflict() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CloseDoors\"}"))
                .andExpect(status().isConflict());
    }

    // "close-doors refused while obstructed returns a problem carrying
    // open-doors" -- the test named in this slice's roadmap entry.
    @Test
    void closingObstructedDoorsIsAConflictCarryingOpenDoors() throws Exception {
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.DoorsOpen(),
                new Doors(Doors.DoorPosition.OPEN, true),
                new Load(0, 800),
                RequestQueue.empty()));

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CloseDoors\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Obstruction")))
                .andExpect(content().string(containsString("open-doors")));
    }

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"CloseDoors\"}"))
                .andExpect(status().isNotFound());
    }
}
