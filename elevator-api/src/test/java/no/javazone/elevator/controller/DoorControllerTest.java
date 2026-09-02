package no.javazone.elevator.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import no.javazone.elevator.TestJwtDecoderConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class DoorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openDoorsOnIdleElevatorOpensDoors() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doorState", is("OPEN")))
                .andExpect(jsonPath("$.state", is("DOORS_OPEN")));
    }

    @Test
    void closeDoorsAfterOpenTransitionsToClosing() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));

        mockMvc.perform(post("/elevators/1/close-doors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doorState", is("CLOSING")))
                .andExpect(jsonPath("$.state", is("DOORS_CLOSING")));
    }

    @Test
    void closeDoorsWhenAlreadyClosedReturnsConflict() throws Exception {
        mockMvc.perform(post("/elevators/1/close-doors"))
                .andExpect(status().isConflict());
    }

    @Test
    void openDoorsWhileMovingReturnsConflict() throws Exception {
        mockMvc.perform(post("/elevators/1/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 9, \"direction\": \"UP\"}"));

        mockMvc.perform(post("/elevators/1/open-doors"))
                .andExpect(status().isConflict());
    }

    @Test
    void obstructionReopensClosingDoors() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));
        mockMvc.perform(post("/elevators/1/close-doors"));

        mockMvc.perform(put("/elevators/1/obstruction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"obstructed\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.obstructed", is(true)));

        // On next read, obstruction should have triggered re-open
        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doorState", is("OPEN")))
                .andExpect(jsonPath("$.state", is("DOORS_OPEN")));
    }

    @Test
    void closeDoorsWhenObstructedReturnsConflict() throws Exception {
        mockMvc.perform(put("/elevators/1/obstruction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"obstructed\": true}"));
        mockMvc.perform(post("/elevators/1/open-doors"));

        mockMvc.perform(post("/elevators/1/close-doors"))
                .andExpect(status().isConflict());
    }

    @Test
    void clearObstructionAllowsDoorsToClose() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));

        mockMvc.perform(post("/elevators/1/close-doors"))
                .andExpect(status().isOk());

        // With obstruction cleared, doors should proceed to IDLE after timeout
        Thread.sleep(2500);

        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doorState", is("CLOSED")))
                .andExpect(jsonPath("$.state", is("IDLE")));
    }
}
