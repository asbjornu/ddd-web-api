package no.javazone.elevator.feature.entermaintenance;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import no.javazone.elevator.TestJwtDecoderConfig;
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
 * {@link EnterMaintenanceController}'s Javadoc. These tests exercise
 * both halves of that same predicate: authority (a token with the
 * wrong scope, or none at all) and availability (state), the same way
 * every other command's tests exercise refusal.
 */
@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class EnterMaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static JwtRequestPostProcessor maintenanceToken() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_elevator:maintenance"));
    }

    private static JwtRequestPostProcessor recallToken() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_elevator:recall"));
    }

    @Test
    void enteringMaintenanceWithTheScopeSucceeds() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .with(maintenanceToken())
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"EnterMaintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\" : \"outOfService\"")));
    }

    @Test
    void enteringMaintenanceWithoutTheScopeIsForbidden() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .with(recallToken())
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"EnterMaintenance\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void enteringMaintenanceAnonymouslyIsForbidden() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"EnterMaintenance\"}"))
                .andExpect(status().isForbidden());
    }

    // Mid-recall refusal is exercised at the domain level only
    // (ElevatorMaintenanceTest), not through the full HTTP/persistence
    // stack: EmergencyRecall cannot round-trip through the aggregate
    // store yet -- ElevatorStateNames.fromName's own Javadoc says that
    // arrives with slice 7.

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999")
                        .with(maintenanceToken())
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"EnterMaintenance\"}"))
                .andExpect(status().isNotFound());
    }
}
