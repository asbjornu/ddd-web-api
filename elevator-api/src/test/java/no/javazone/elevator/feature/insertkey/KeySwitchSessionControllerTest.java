package no.javazone.elevator.feature.insertkey;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import no.javazone.elevator.TestJwtDecoderConfig;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST}/{@code DELETE /elevators/{id}/key-switch/session}.
 * {@link TechnicianTokenExchange} -- the one call that actually reaches
 * {@code elevator-auth} -- is mocked here rather than exercised for
 * real, the same way a unit test never starts a real database; the
 * exchange's own worked, end-to-end proof (a real token, a real
 * cookie, a real cookie-only command) lives outside the test suite,
 * against a running {@code elevator-auth}, per this controller's
 * Javadoc.
 */
@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class KeySwitchSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TechnicianTokenExchange tokenExchange;

    @Test
    void establishingASessionWithAValidSecretSetsTheCookie() throws Exception {
        when(tokenExchange.exchange(eq("dev-secret-key"))).thenReturn(Optional.of(
                new TechnicianTokenExchange.Token(
                        "the-access-token", "elevator:maintenance elevator:recall", 900)));

        mockMvc.perform(post("/elevators/1/key-switch/session")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\": \"dev-secret-key\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        matchesPattern("(?s).*technician_token=the-access-token.*HttpOnly.*")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/elevators")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(content().string(containsString("enter-maintenance")));
    }

    @Test
    void establishingASessionWithAWrongSecretIsUnauthorized() throws Exception {
        when(tokenExchange.exchange(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/elevators/1/key-switch/session")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\": \"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void establishingASessionWithNoSecretIsTheRfc9728Challenge() throws Exception {
        mockMvc.perform(post("/elevators/1/key-switch/session")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        containsString("resource_metadata=\"/.well-known/oauth-protected-resource\"")));
    }

    @Test
    void establishingASessionForAnUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999/key-switch/session")
                        .header(HttpHeaders.ACCEPT, "application/problem+json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secret\": \"dev-secret-key\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawingClearsTheCookieAndDropsTechnicianAffordances() throws Exception {
        mockMvc.perform(delete("/elevators/1/key-switch/session")
                        .header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("technician_token=;")))
                .andExpect(content().string(Matchers.not(containsString("enter-maintenance"))));
    }
}
