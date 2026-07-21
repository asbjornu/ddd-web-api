package no.javazone.elevator.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
class MaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String KEY_HEADER = "X-Technician-Key";
    private static final String VALID_KEY = "dev-secret-key";

    @Test
    void enterMaintenanceTransitionsToOutOfService() throws Exception {
        mockMvc.perform(post("/elevators/1/maintenance")
                        .header(KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("OUT_OF_SERVICE")))
                .andExpect(jsonPath("$.doorState", is("CLOSED")))
                .andExpect(jsonPath("$.direction", is("NONE")));
    }

    @Test
    void exitMaintenanceReturnsToIdle() throws Exception {
        mockMvc.perform(post("/elevators/1/maintenance")
                        .header(KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"));

        mockMvc.perform(post("/elevators/1/maintenance")
                        .header(KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("IDLE")))
                .andExpect(jsonPath("$.doorState", is("CLOSED")))
                .andExpect(jsonPath("$.direction", is("NONE")));
    }

    @Test
    void enterMaintenanceClearsPendingCalls() throws Exception {
        mockMvc.perform(post("/elevators/1/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 5, \"direction\": \"UP\"}"));
        mockMvc.perform(post("/elevators/1/car-calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 7}"));

        mockMvc.perform(post("/elevators/1/maintenance")
                        .header(KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"));

        mockMvc.perform(get("/elevators/1/calls"))
                .andExpect(jsonPath("$[0].servedAt", is(notNullValue())));
        mockMvc.perform(get("/elevators/1/car-calls"))
                .andExpect(jsonPath("$[0].servedAt", is(notNullValue())));
    }

    @Test
    void enterMaintenanceRejectsInvalidKey() throws Exception {
        mockMvc.perform(post("/elevators/1/maintenance")
                        .header(KEY_HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enterMaintenanceRejectsMissingKey() throws Exception {
        mockMvc.perform(post("/elevators/1/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"))
                .andExpect(status().isBadRequest());
    }
}
