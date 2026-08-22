package no.javazone.elevator.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class WeightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void setWeightBelowCapacityIsAccepted() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));

        mockMvc.perform(post("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg", is(500)));
    }

    @Test
    void setWeightWhenDoorsClosedReturnsConflict() throws Exception {
        mockMvc.perform(post("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 500}"))
                .andExpect(status().isConflict());
    }

    @Test
    void overloadHoldsDoorsOpenAndClearsCarCalls() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));

        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 5}"));

        mockMvc.perform(post("/elevators/1/weight")
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
        mockMvc.perform(post("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 900}"));

        mockMvc.perform(post("/elevators/1/close-doors"))
                .andExpect(status().isConflict());
    }

    @Test
    void selectFloorWhenOverloadedReturnsConflict() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));
        mockMvc.perform(post("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 900}"));

        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 5}"))
                .andExpect(status().isConflict());
    }

    @Test
    void reduceWeightResumesNormalOperation() throws Exception {
        mockMvc.perform(post("/elevators/1/open-doors"));
        mockMvc.perform(post("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 900}"));

        // Weight back below capacity
        mockMvc.perform(post("/elevators/1/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWeightKg", is(0)));

        // Now close-doors should work
        mockMvc.perform(post("/elevators/1/close-doors"))
                .andExpect(status().isOk());
    }
}
