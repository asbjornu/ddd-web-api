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
 * The minimal bespoke format from the "REST State Machine Revisited"
 * article: plain properties, plain links, and an {@code operations}
 * array naming each legal command. Kept only as a teaching device -- see
 * {@code docs/plan.html} &sect;18 -- not a recommendation; ship the
 * standards (Siren, Hydra) and keep this one for the projector.
 */
@Component
public class ElevatorStateJsonRenderer implements Renderer {

    private final ObjectMapper objectMapper;

    public ElevatorStateJsonRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MediaType mediaType() {
        return ElevatorMediaTypes.ELEVATOR_STATE_JSON;
    }

    @Override
    public String render(Representation representation) {
        ObjectNode root = objectMapper.createObjectNode();
        representation.properties().forEach((name, value) -> root.putPOJO(name, value));

        ArrayNode links = root.putArray("links");
        for (Link link : representation.links()) {
            ObjectNode node = links.addObject();
            node.put("rel", link.rel());
            node.put("href", link.href());
            if (link.type() != null) {
                node.put("type", link.type());
            }
        }

        ArrayNode operations = root.putArray("operations");
        for (Affordance affordance : representation.affordances()) {
            operations.add(operationNode(affordance));
        }
        return root.toPrettyString();
    }

    private ObjectNode operationNode(Affordance affordance) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("rel", affordance.rel());
        node.put("title", affordance.title());
        node.put("method", affordance.method());
        node.put("href", affordance.href());
        if (!affordance.fields().isEmpty()) {
            ArrayNode fields = node.putArray("fields");
            for (Field field : affordance.fields()) {
                ObjectNode fieldNode = fields.addObject();
                fieldNode.put("name", field.name());
                fieldNode.put("type", field.type());
                fieldNode.putPOJO("value", field.value());
                fieldNode.put("required", field.required());
                if (!field.options().isEmpty()) {
                    fieldNode.putPOJO("options", field.options());
                }
            }
        }
        return node;
    }
}
