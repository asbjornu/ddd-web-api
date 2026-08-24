package no.javazone.elevator.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import no.javazone.elevator.TestJwtDecoderConfig;
import no.javazone.elevator.model.CarCall;
import no.javazone.elevator.service.ElevatorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class WeightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorService elevatorService;

    private static CarCall carCallFor(int floor) {
        CarCall request = new CarCall();
        request.setFloor(floor);
        return request;
    }

    @Test
    void setWeightBelowCapacityIsAccepted() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));

        mockMvc.perform(put("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg", is(500)));
    }

    @Test
    void setWeightWhenDoorsClosedReturnsConflict() throws Exception {
        mockMvc.perform(put("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 500}"))
                .andExpect(status().isConflict());
    }

    @Test
    void overloadHoldsDoorsOpenAndClearsCarCalls() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));

        // /car-calls now reaches the new aggregate (slice 3); the old
        // service method is what still characterises this old-system
        // overload behaviour.
        elevatorService.carCall(1L, carCallFor(5));

        mockMvc.perform(put("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 900}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg", is(900)));

        // Car call at current floor should be cleared
        mockMvc.perform(get("/elevators/1/car-calls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].servedAt", is(notNullValue())));

        // Doors should stay open (stateSince reset, so auto-close won't fire immediately)
        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(jsonPath("$.doorState", is("OPEN")))
                .andExpect(jsonPath("$.state", is("DOORS_OPEN")));
    }

    @Test
    void closeDoorsWhenOverloadedReturnsConflict() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));
        mockMvc.perform(put("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 900}"));

        mockMvc.perform(post("/elevators/1/close-doors"))
                .andExpect(status().isConflict());
    }

    @Test
    void selectFloorWhenOverloadedReturnsConflict() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));
        mockMvc.perform(put("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 900}"));

        assertThatThrownBy(() -> elevatorService.carCall(1L, carCallFor(5)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void reduceWeightResumesNormalOperation() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));
        mockMvc.perform(put("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 900}"));

        // Weight back below capacity
        mockMvc.perform(put("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg", is(0)));

        // Now close-doors should work
        mockMvc.perform(post("/elevators/1/close-doors"))
                .andExpect(status().isOk());
    }
}
