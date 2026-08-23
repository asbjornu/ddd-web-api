package no.javazone.elevator.shared.render;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import java.util.List;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.Field;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import org.junit.jupiter.api.Test;

/**
 * All four formats must expose the same affordance set for the same
 * representation -- see {@code docs/plan.html} &sect;0's "Renderer
 * conformance" test named in slice 0's own description. This does not
 * assert byte-for-byte equality (each format's shape differs on
 * purpose) -- it asserts that the rel, the method and the field name
 * survive every renderer.
 */
class RendererConformanceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Representation sample = Representation.builder("Elevator")
            .property("currentFloor", 3)
            .link(new Link("self", "/elevators/1"))
            .affordance(new Affordance(
                    "select-floor",
                    "Select a floor",
                    "PUT",
                    "/elevators/1/car-calls",
                    List.of(Field.text("floor", 5))))
            .build();

    private final List<Renderer> renderers = List.of(
            new HtmlRenderer(),
            new ElevatorStateJsonRenderer(objectMapper),
            new SirenRenderer(objectMapper),
            new HydraRenderer(objectMapper));

    @Test
    void everyRendererCarriesTheAffordancesRelAndMethod() {
        for (Renderer renderer : renderers) {
            String body = renderer.render(sample);
            assertThat(body)
                    .as("rendered by %s", renderer.getClass().getSimpleName())
                    .contains("select-floor")
                    .contains("PUT")
                    .contains("floor");
        }
    }

    @Test
    void everyRendererCarriesTheSelfLink() {
        for (Renderer renderer : renderers) {
            String body = renderer.render(sample);
            assertThat(body)
                    .as("rendered by %s", renderer.getClass().getSimpleName())
                    .contains("/elevators/1");
        }
    }

    @Test
    void anEmptyAffordanceListRendersNoOperationsAnywhere() {
        Representation noAffordances = Representation.builder("Elevator")
                .property("currentFloor", 3)
                .build();
        for (Renderer renderer : renderers) {
            String body = renderer.render(noAffordances);
            assertThat(body)
                    .as("rendered by %s", renderer.getClass().getSimpleName())
                    .doesNotContain("select-floor");
        }
    }
}
