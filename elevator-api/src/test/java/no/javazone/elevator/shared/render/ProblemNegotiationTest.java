package no.javazone.elevator.shared.render;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import no.javazone.elevator.shared.hypermedia.Representation;
import org.junit.jupiter.api.Test;

/**
 * The negotiation table from {@code docs/plan.html} &sect;6's
 * "Negotiating a failure": honour the client's stated preference, and
 * where preferences are equal prefer {@code problem+json}.
 */
class ProblemNegotiationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemJsonRenderer problemRenderer = new ProblemJsonRenderer(objectMapper);
    private final RendererRegistry registry = new RendererRegistry(
            java.util.List.of(
                    new HtmlRenderer(),
                    new ElevatorStateJsonRenderer(objectMapper),
                    new SirenRenderer(objectMapper),
                    new HydraRenderer(objectMapper),
                    problemRenderer),
            problemRenderer);

    private final Representation problem = Representation.builder("Refused")
            .property("type", "about:blank")
            .property("title", "Not Acceptable")
            .property("status", 406)
            .property("detail", "no usable representation")
            .build();

    @Test
    void explicitlyAcceptingBothPrefersProblemJson() {
        Renderer chosen = registry.selectForFailure(
                "application/vnd.siren+json, application/problem+json");
        assertThat(chosen).isInstanceOf(ProblemJsonRenderer.class);
    }

    @Test
    void acceptingSirenAloneRendersTheProblemAsSiren() {
        Renderer chosen = registry.selectForFailure("application/vnd.siren+json");
        assertThat(chosen).isInstanceOf(SirenRenderer.class);

        String body = chosen.render(problem);
        // Problem members as properties, per the plan's own table --
        // not promoted to a Siren-specific "problem" shape.
        assertThat(body).contains("\"status\" : 406");
        assertThat(body).contains("\"title\" : \"Not Acceptable\"");
    }

    @Test
    void acceptingHtmlRendersAProblemPage() {
        Renderer chosen = registry.selectForFailure("text/html");
        assertThat(chosen).isInstanceOf(HtmlRenderer.class);
    }

    @Test
    void noUsablePreferenceDefaultsToProblemJson() {
        assertThat(registry.selectForFailure(null)).isInstanceOf(ProblemJsonRenderer.class);
        assertThat(registry.selectForFailure("*/*")).isInstanceOf(ProblemJsonRenderer.class);
    }

    @Test
    void successNegotiationNeverOffersProblemJson() {
        assertThat(registry.select("*/*")).isPresent();
        assertThat(registry.select("*/*").orElseThrow())
                .isNotInstanceOf(ProblemJsonRenderer.class);
    }

    @Test
    void successNegotiationHonoursAnExplicitFormat() {
        assertThat(registry.select("application/vnd.elevator.state+json").orElseThrow())
                .isInstanceOf(ElevatorStateJsonRenderer.class);
    }

    @Test
    void successNegotiationReturnsEmptyWhenNothingMatches() {
        assertThat(registry.select("application/xml")).isEmpty();
    }
}
