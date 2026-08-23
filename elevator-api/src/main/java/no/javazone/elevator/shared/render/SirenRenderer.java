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
 * Siren (application/vnd.siren+json): the complete standard format,
 * scoring every H-Factor link and control-data factor -- see
 * {@code docs/plan.html} &sect;7's "Scoring the formats". Properties,
 * links and actions map directly onto {@link Representation}'s own
 * shape, which is the point: nothing here is Siren-specific except the
 * key names.
 */
@Component
public class SirenRenderer implements Renderer {

    private final ObjectMapper objectMapper;

    public SirenRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MediaType mediaType() {
        return ElevatorMediaTypes.SIREN_JSON;
    }

    @Override
    public String render(Representation representation) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("class").add(representation.title());

        ObjectNode properties = root.putObject("properties");
        representation.properties().forEach((name, value) -> properties.putPOJO(name, value));

        ArrayNode links = root.putArray("links");
        for (Link link : representation.links()) {
            ObjectNode node = links.addObject();
            node.putArray("rel").add(link.rel());
            node.put("href", link.href());
            if (link.type() != null) {
                node.put("type", link.type());
            }
        }

        ArrayNode actions = root.putArray("actions");
        for (Affordance affordance : representation.affordances()) {
            actions.add(actionNode(affordance));
        }
        return root.toPrettyString();
    }

    private ObjectNode actionNode(Affordance affordance) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", affordance.rel());
        node.put("title", affordance.title());
        node.put("method", affordance.method());
        node.put("href", affordance.href());
        if (!affordance.fields().isEmpty()) {
            ArrayNode fields = node.putArray("fields");
            for (Field field : affordance.fields()) {
                ObjectNode fieldNode = fields.addObject();
                fieldNode.put("name", field.name());
                // Siren's permitted field types are the HTML5 input
                // types; "select" is not among them (Swiber's still-open
                // 2012 proposal). We emit it anyway, as the community
                // convention this format speaks -- see docs/plan.html
                // section 9's "The rule hypermedia can break".
                fieldNode.put("type", field.type());
                fieldNode.putPOJO("value", field.value());
                if (!field.options().isEmpty()) {
                    fieldNode.putPOJO("options", field.options());
                }
            }
        }
        return node;
    }
}
