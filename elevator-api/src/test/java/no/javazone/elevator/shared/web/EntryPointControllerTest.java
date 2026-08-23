package no.javazone.elevator.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
class EntryPointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void negotiatesHtml() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("Elevator API")));
    }

    @Test
    void negotiatesElevatorStateJson() throws Exception {
        mockMvc.perform(get("/").header(
                        HttpHeaders.ACCEPT, "application/vnd.elevator.state+json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.elevator.state+json"));
    }

    @Test
    void negotiatesSiren() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.siren+json"));
    }

    @Test
    void negotiatesJsonLd() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.ACCEPT, "application/ld+json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/ld+json"));
    }

    @Test
    void carriesALinkHeaderPerLink() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.LINK, containsString("rel=\"self\"")));
    }

    @Test
    void refusesAnUnsupportedFormatWithAProblem() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.ACCEPT, "application/xml"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().string(containsString("Not Acceptable")));
    }
}
