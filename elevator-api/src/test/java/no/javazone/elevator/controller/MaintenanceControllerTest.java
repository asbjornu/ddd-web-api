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

    private static final String AUTH_HEADER = "Authorization";
    private static final String VALID_TOKEN = "Bearer dev-secret-key";

    @Test
    void enterMaintenanceTransitionsToOutOfService() throws Exception {
        mockMvc.perform(post("/elevators/1/maintenance")
                        .header(AUTH_HEADER, VALID_TOKEN)
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
                        .header(AUTH_HEADER, VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"));

        mockMvc.perform(post("/elevators/1/maintenance")
                        .header(AUTH_HEADER, VALID_TOKEN)
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
                        .header(AUTH_HEADER, VALID_TOKEN)
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
                        .header(AUTH_HEADER, "Bearer wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enterMaintenanceRejectsMissingAuthHeader() throws Exception {
        mockMvc.perform(post("/elevators/1/maintenance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enterMaintenanceRejectsNonBearerAuth() throws Exception {
        mockMvc.perform(post("/elevators/1/maintenance")
                        .header(AUTH_HEADER, "Basic dGVzdDp0ZXN0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maintenance\": true}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emergencyRecallSetsDirectionToRecallFloor() throws Exception {
        mockMvc.perform(post("/elevators/1/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 3, \"direction\": \"UP\"}"));

        Thread.sleep(5000);

        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .header(AUTH_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("EMERGENCY_RECALL")))
                .andExpect(jsonPath("$.direction", is("DOWN")))
                .andExpect(jsonPath("$.targetFloor", is(1)));
    }

    @Test
    void emergencyRecallAtRecallFloorGoesDirectlyToOutOfService() throws Exception {
        // Move elevator to floor 1 (the recall floor)
        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .header(AUTH_HEADER, VALID_TOKEN));

        // Already at recall floor — should go straight to OUT_OF_SERVICE
        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .header(AUTH_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("OUT_OF_SERVICE")))
                .andExpect(jsonPath("$.doorState", is("CLOSED")));
    }

    @Test
    void emergencyRecallArrivesAtOutOfServiceAfterTravel() throws Exception {
        mockMvc.perform(post("/elevators/1/calls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"floor\": 2, \"direction\": \"UP\"}"));

        Thread.sleep(2500);

        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .header(AUTH_HEADER, VALID_TOKEN))
                .andExpect(jsonPath("$.state", is("EMERGENCY_RECALL")))
                .andExpect(jsonPath("$.direction", is("DOWN")))
                .andExpect(jsonPath("$.targetFloor", is(1)));

        Thread.sleep(2500);

        mockMvc.perform(get("/elevators/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("OUT_OF_SERVICE")))
                .andExpect(jsonPath("$.currentFloor", is(1)));
    }

    @Test
    void emergencyRecallRejectsInvalidKey() throws Exception {
        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .header(AUTH_HEADER, "Bearer wrong-key"))
                .andExpect(status().isUnauthorized());
    }
}
