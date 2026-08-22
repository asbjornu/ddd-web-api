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

/**
 * Car calls: destination floors selected from inside the car.
 *
 * <p>This is the slice 3 counterpart to {@link CallControllerTest}, and was
 * the only slice without a test class of its own -- it had been covered
 * only incidentally, through the overload case in
 * {@link WeightControllerTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CarCallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void selectingAFloorFromIdleStartsTheElevatorMoving() throws Exception {
        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 4}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.floor", is(4)))
                .andExpect(jsonPath("$.servedAt").doesNotExist());

        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("MOVING_UP")))
                .andExpect(jsonPath("$.direction", is("UP")))
                .andExpect(jsonPath("$.targetFloor", is(4)));
    }

    @Test
    void listsPendingCarCalls() throws Exception {
        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 6}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/elevators/1/car-calls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].floor", is(6)))
                .andExpect(jsonPath("$[0].elevatorId", is(1)));
    }

    @Test
    void rejectsFloorAboveTheBuilding() throws Exception {
        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFloorBelowTheBuilding() throws Exception {
        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSelectingTheFloorTheCarIsAlreadyStoppedAt() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"))
                .andExpect(status().isOk());

        // The seeded elevator sits at floor 1 with its doors now open.
        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsCarCallsWhileOutOfService() throws Exception {
        mockMvc.perform(post("/elevators/1/maintenance")
                        .header("Authorization", "Bearer dev-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aSecondCarCallDoesNotRetargetAJourneyAlreadyUnderway() throws Exception {
        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 7}"))
                .andExpect(status().isCreated());

        // Floor 3 is nearer and on the way, but the car is committed to 7 and
        // only reconsiders when it next becomes idle.
        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 3}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetFloor", is(7)));
    }
}
