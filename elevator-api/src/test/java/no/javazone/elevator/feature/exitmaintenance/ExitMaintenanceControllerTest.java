package no.javazone.elevator.feature.exitmaintenance;

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

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class ExitMaintenanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorAggregateStore store;

    private static JwtRequestPostProcessor maintenanceToken() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_elevator:maintenance"));
    }

    private void seedOutOfService() {
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(1),
                new ElevatorState.OutOfService(),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty()));
    }

    @Test
    void exitingMaintenanceWithTheScopeSucceeds() throws Exception {
        seedOutOfService();

        mockMvc.perform(post("/elevators/1")
                        .with(maintenanceToken())
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ExitMaintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\" : \"idle\"")));
    }

    @Test
    void exitingMaintenanceWithoutTheScopeIsForbidden() throws Exception {
        seedOutOfService();

        mockMvc.perform(post("/elevators/1")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ExitMaintenance\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exitingMaintenanceWhenNotInMaintenanceIsAConflict() throws Exception {
        mockMvc.perform(post("/elevators/1")
                        .with(maintenanceToken())
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ExitMaintenance\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999")
                        .with(maintenanceToken())
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"ExitMaintenance\"}"))
                .andExpect(status().isNotFound());
    }
}
