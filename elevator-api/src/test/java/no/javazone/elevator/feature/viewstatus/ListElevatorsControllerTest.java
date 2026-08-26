package no.javazone.elevator.feature.viewstatus;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import no.javazone.elevator.TestJwtDecoderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /elevators}: the collection a client follows to discover
 * which elevators exist, rather than constructing {@code /elevators/1}
 * itself -- see {@link ListElevatorsController}'s Javadoc.
 */
@SpringBootTest
@Import(TestJwtDecoderConfig.class)
@AutoConfigureMockMvc
class ListElevatorsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void carriesALinkToTheSeededElevator() throws Exception {
        mockMvc.perform(get("/elevators").header(HttpHeaders.ACCEPT, "application/vnd.siren+json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/elevators/1")));
    }

    @Test
    void negotiatesHtml() throws Exception {
        mockMvc.perform(get("/elevators").header(HttpHeaders.ACCEPT, "text/html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("/elevators/1")));
    }
}
