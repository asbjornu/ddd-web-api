package no.javazone.elevator.feature.triggeremergencyrecall;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scope check happens inside the command, not in front of it -- see
 * {@link TriggerEmergencyRecallController}'s Javadoc, and {@code
 * EnterMaintenanceControllerTest}'s equivalents for the same predicate
 * shape.
 */
@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class TriggerEmergencyRecallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorAggregateStore store;

    private static JwtRequestPostProcessor recallToken() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_elevator:recall"));
    }

    private static JwtRequestPostProcessor maintenanceToken() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_elevator:maintenance"));
    }

    private void seedAtRecallFloor() {
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(1, true),
                new ElevatorState.Idle(),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty()));
    }

    private void seedAwayFromRecallFloor() {
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(3),
                new ElevatorState.Idle(),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty()));
    }

    @Test
    void triggeringRecallWithTheScopeSettlesImmediatelyWhenAlreadyAtTheRecallFloor()
            throws Exception {
        seedAtRecallFloor();

        mockMvc.perform(post("/elevators/1")
                        .with(recallToken())
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"TriggerEmergencyRecall\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\" : \"outOfService\"")));
    }

    @Test
    void triggeringRecallWithTheScopeStartsTravelWhenElsewhere() throws Exception {
        seedAwayFromRecallFloor();

        mockMvc.perform(post("/elevators/1")
                        .with(recallToken())
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"TriggerEmergencyRecall\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\" : \"emergencyRecall\"")));
    }

    @Test
    void triggeringRecallWithoutTheScopeIsForbidden() throws Exception {
        seedAwayFromRecallFloor();

        mockMvc.perform(post("/elevators/1")
                        .with(maintenanceToken())
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"TriggerEmergencyRecall\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void triggeringRecallAnonymouslyIsForbidden() throws Exception {
        seedAwayFromRecallFloor();

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"TriggerEmergencyRecall\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999")
                        .with(recallToken())
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"TriggerEmergencyRecall\"}"))
                .andExpect(status().isNotFound());
    }
}
