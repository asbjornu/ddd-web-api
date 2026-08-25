package no.javazone.elevator.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emergency recall only -- {@code enterMaintenance}/{@code
 * exitMaintenance} characterisation tests moved to {@code
 * feature.entermaintenance}/{@code feature.exitmaintenance} in slice 6;
 * this class awaits slice 7 unchanged. See {@link MaintenanceController}'s
 * Javadoc.
 */
@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class MaintenanceControllerTest {

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
    void emergencyRecallSetsDirectionToRecallFloor() throws Exception {
        elevatorService.carCall(1L, carCallFor(3));

        Thread.sleep(5000);

        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .with(recallToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("EMERGENCY_RECALL")))
                .andExpect(jsonPath("$.direction", is("DOWN")))
                .andExpect(jsonPath("$.targetFloor", is(1)));
    }

    @Test
    void emergencyRecallAtRecallFloorGoesDirectlyToOutOfService() throws Exception {
        // Move elevator to floor 1 (the recall floor)
        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .with(recallToken()));

        // Already at recall floor — should go straight to OUT_OF_SERVICE
        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .with(recallToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("OUT_OF_SERVICE")))
                .andExpect(jsonPath("$.doorState", is("CLOSED")));
    }

    @Test
    void emergencyRecallArrivesAtOutOfServiceAfterTravel() throws Exception {
        elevatorService.carCall(1L, carCallFor(2));

        Thread.sleep(2500);

        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .with(recallToken()))
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
    void emergencyRecallRejectsATokenWithoutTheScope() throws Exception {
        mockMvc.perform(post("/elevators/1/emergency-recall")
                        .with(maintenanceToken()))
                .andExpect(status().isForbidden());
    }

    /**
     * A token carrying only {@code elevator:maintenance}. The scopes are not
     * interchangeable: this one cannot trigger a recall, which is the
     * separation a single shared secret was incapable of expressing.
     */
    private static JwtRequestPostProcessor maintenanceToken() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_elevator:maintenance"));
    }

    /** A token carrying only {@code elevator:recall}. */
    private static JwtRequestPostProcessor recallToken() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_elevator:recall"));
    }
}
