package no.javazone.elevator.shared.scheduler;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import no.javazone.elevator.TestJwtDecoderConfig;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.Doors;
import no.javazone.elevator.shared.domain.Elevator;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.ElevatorState;
import no.javazone.elevator.shared.domain.Floor;
import no.javazone.elevator.shared.domain.Load;
import no.javazone.elevator.shared.domain.RequestQueue;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import org.junit.jupiter.api.AfterEach;
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

/**
 * "Scheduler fires at the computed instant", the same test shape as
 * {@link MovementSchedulerIntegrationTest}, applied to the journey
 * {@link RecallScheduler} completes. Deliberately not
 * {@code @Transactional} for the same reason.
 */
@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
class RecallSchedulerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ElevatorAggregateStore store;

    @Autowired
    private ElevatorViewProjection projection;

    private static JwtRequestPostProcessor recallToken() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_elevator:recall"));
    }

    @AfterEach
    void reseedTheElevator() {
        Elevator seeded = Elevator.seed(new ElevatorId(1), new Floor(1), 800);
        store.save(seeded);
        projection.syncFrom(seeded);
    }

    @Test
    void theCarActuallySettlesOutOfServiceAtTheComputedInstant() throws Exception {
        // Default config: recall floor 1, seeded car starts at floor 1.
        // Move it to floor 3 first so recall has one floor to travel.
        store.save(Elevator.restore(
                new ElevatorId(1),
                new Floor(3),
                new ElevatorState.Idle(),
                Doors.closed(),
                new Load(0, 800),
                RequestQueue.empty()));
        projection.syncFrom(store.find(new ElevatorId(1)).orElseThrow());

        // Default config travels 2 s/floor; recall floor 1 is two floors away.
        mockMvc.perform(post("/elevators/1")
                        .with(recallToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"TriggerEmergencyRecall\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"state\" : \"emergencyRecall\"")));

        Thread.sleep(4600);

        mockMvc.perform(get("/elevators/1").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"currentFloor\" : 1")))
                .andExpect(content().string(containsString("\"state\" : \"outOfService\"")));
    }
}
