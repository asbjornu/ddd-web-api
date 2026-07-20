package no.javazone.elevator.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void callingFromIdleStartsTheElevatorMoving() throws Exception {
        mockMvc.perform(post("/elevators/1/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 3, \"direction\": \"UP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.floor", is(3)))
                .andExpect(jsonPath("$.direction", is("UP")));

        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("MOVING_UP")))
                .andExpect(jsonPath("$.direction", is("UP")))
                .andExpect(jsonPath("$.currentFloor", is(1)));
    }

    @Test
    void elevatorArrivesAndOpensDoorsAfterTravelTime() throws Exception {
        mockMvc.perform(post("/elevators/1/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 2, \"direction\": \"UP\"}"))
                .andExpect(status().isCreated());

        // Default config: ~2 seconds of travel per floor, distance 1 floor.
        Thread.sleep(2500);

        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("DOORS_OPEN")))
                .andExpect(jsonPath("$.direction", is("NONE")))
                .andExpect(jsonPath("$.currentFloor", is(2)));

        mockMvc.perform(get("/elevators/1/calls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].servedAt").isNotEmpty());
    }

    @Test
    void rejectsCallForFloorOutsideBuilding() throws Exception {
        mockMvc.perform(post("/elevators/1/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 99, \"direction\": \"UP\"}"))
                .andExpect(status().isBadRequest());
    }
}
