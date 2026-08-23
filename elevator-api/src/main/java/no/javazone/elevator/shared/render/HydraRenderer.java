package no.javazone.elevator.shared.render;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.Field;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * JSON-LD with a Hydra-shaped vocabulary: every term is an IRI, so
 * "what does this property mean" cannot arise the way it can for a
 * bespoke tool name -- see {@code docs/plan.html} &sect;17's "The
 * vocabulary argument". The {@code @context} here is a placeholder for
 * this API's own rel vocabulary rather than a real Hydra ApiDocumentation
 * yet; wiring one is future work, not this slice's.
 */
@Component
public class HydraRenderer implements Renderer {

    private final ObjectMapper objectMapper;

    public HydraRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MediaType mediaType() {
        return ElevatorMediaTypes.JSON_LD;
    }

    @Override
    public String render(Representation representation) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode context = root.putObject("@context");
        context.put("hydra", "http://www.w3.org/ns/hydra/core#");
        context.put("rels", "/rels/");
        root.put("@type", representation.title());

        representation.properties().forEach((name, value) -> root.putPOJO(name, value));

        ArrayNode links = root.putArray("links");
        for (Link link : representation.links()) {
            ObjectNode node = links.addObject();
            node.put("@id", link.href());
            node.put("rel", link.rel());
        }

        ArrayNode operations = root.putArray("hydra:operation");
        for (Affordance affordance : representation.affordances()) {
            operations.add(operationNode(affordance));
        }
        return root.toPrettyString();
    }

    private ObjectNode operationNode(Affordance affordance) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("hydra:title", affordance.title());
        node.put("hydra:method", affordance.method());
        node.put("@id", affordance.href());
        node.put("rel", affordance.rel());
        if (!affordance.fields().isEmpty()) {
            ArrayNode fields = node.putArray("hydra:expects");
            for (Field field : affordance.fields()) {
                ObjectNode fieldNode = fields.addObject();
                fieldNode.put("name", field.name());
                fieldNode.putPOJO("defaultValue", field.value());
                fieldNode.put("required", field.required());
                if (!field.options().isEmpty()) {
                    // The sanctioned Hydra answer for permitted values
                    // is SHACL's sh:in via the Hydra-SHACL extension,
                    // not a bespoke array -- see docs/plan.html section
                    // 9. Approximated here rather than pulled in, since
                    // this slice has no field with options to render.
                    fieldNode.putPOJO("options", field.options());
                }
            }
        }
        return node;
    }
}
