package no.javazone.elevator.feature.insertkey;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import no.javazone.elevator.TestJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
@Transactional
class InsertKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void followingInsertKeyGetsTheRfc9728Challenge() throws Exception {
        mockMvc.perform(post("/elevators/1/key-switch")
                        .header(HttpHeaders.ACCEPT, "application/problem+json"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        containsString("resource_metadata=\"/.well-known/oauth-protected-resource\"")))
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        containsString("scope=\"elevator:maintenance elevator:recall\"")));
    }

    @Test
    void anUnknownElevatorIsNotFound() throws Exception {
        mockMvc.perform(post("/elevators/999/key-switch")
                        .header(HttpHeaders.ACCEPT, "application/problem+json"))
                .andExpect(status().isNotFound());
    }
}
