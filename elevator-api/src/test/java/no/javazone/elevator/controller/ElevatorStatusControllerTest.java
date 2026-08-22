package no.javazone.elevator.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import no.javazone.elevator.TestJwtDecoderConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
class ElevatorStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSeededElevatorStatus() throws Exception {
        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.currentFloor", is(1)))
                .andExpect(jsonPath("$.state", is("IDLE")))
                .andExpect(jsonPath("$.direction", is("NONE")))
                .andExpect(jsonPath("$.doorState", is("CLOSED")));
    }

    @Test
    void returnsNotFoundForUnknownElevator() throws Exception {
        mockMvc.perform(get("/elevators/999/status"))
                .andExpect(status().isNotFound());
    }
}
