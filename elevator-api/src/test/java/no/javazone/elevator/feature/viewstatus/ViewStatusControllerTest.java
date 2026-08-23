package no.javazone.elevator.feature.viewstatus;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
class ViewStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void negotiatesSirenForTheSeededElevator() throws Exception {
        mockMvc.perform(get("/elevators/1").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.siren+json"))
                .andExpect(content().string(containsString("\"currentFloor\" : 1")));
    }

    @Test
    void negotiatesHtml() throws Exception {
        mockMvc.perform(get("/elevators/1").header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("currentFloor")));
    }

    @Test
    void carriesALinkToTheEventStream() throws Exception {
        mockMvc.perform(get("/elevators/1").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues(
                        HttpHeaders.LINK, hasItem(containsString("rel=\"updates\""))))
                .andExpect(header().stringValues(
                        HttpHeaders.LINK, hasItem(containsString("/elevators/1/events"))));
    }

    @Test
    void returnsAProblemForAnUnknownElevator() throws Exception {
        mockMvc.perform(get("/elevators/999").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.siren+json"))
                .andExpect(content().string(containsString("\"status\" : 404")));
    }

    @Test
    void returnsAProblemForAnUnparseableSegment() throws Exception {
        mockMvc.perform(get("/elevators/not-a-number")
                        .header(HttpHeaders.ACCEPT, "application/problem+json"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
