package no.javazone.elevator.shared.render;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.Set;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.Field;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * RFC 9457 {@code application/problem+json}: a fifth renderer for
 * refusals, not a special path through the code -- a {@code Problem} is
 * a {@link Representation} like any other, by the convention documented
 * on that class. This renderer alone promotes {@code type}, {@code
 * title}, {@code status}, {@code detail} and {@code instance} to RFC
 * 9457's own top-level members; the other four render them as ordinary
 * properties -- see {@code docs/plan.html} &sect;6.
 *
 * <p>Affordances are carried as an {@code operations} extension member
 * (RFC 9457 &sect;3.2 permits extensions; the RFC's own flagship example
 * already carries one, an {@code accounts} array of links "where the
 * account can be topped up" -- see {@code docs/plan.html} &sect;6's
 * "Carrying affordances in a problem document").
 */
@Component
public class ProblemJsonRenderer implements Renderer {

    private static final Set<String> RFC9457_MEMBERS =
            Set.of("type", "title", "status", "detail", "instance");

    private final ObjectMapper objectMapper;

    public ProblemJsonRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public MediaType mediaType() {
        return ElevatorMediaTypes.PROBLEM_JSON;
    }

    @Override
    public String render(Representation representation) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", stringProperty(representation, "type", "about:blank"));
        root.put("title", stringProperty(representation, "title", representation.title()));
        Object status = representation.properties().get("status");
        if (status != null) {
            root.putPOJO("status", status);
        }
        if (representation.properties().containsKey("detail")) {
            root.put("detail", stringProperty(representation, "detail", ""));
        }
        if (representation.properties().containsKey("instance")) {
            root.put("instance", stringProperty(representation, "instance", ""));
        }

        // Every other property is an extension member, per RFC 9457
        // section 3.2.
        representation.properties().forEach((name, value) -> {
            if (!RFC9457_MEMBERS.contains(name)) {
                root.putPOJO(name, value);
            }
        });

        if (!representation.links().isEmpty()) {
            ArrayNode links = root.putArray("links");
            for (Link link : representation.links()) {
                ObjectNode node = links.addObject();
                node.put("rel", link.rel());
                node.put("href", link.href());
            }
        }

        if (!representation.affordances().isEmpty()) {
            ArrayNode operations = root.putArray("operations");
            for (Affordance affordance : representation.affordances()) {
                operations.add(operationNode(affordance));
            }
        }
        return root.toPrettyString();
    }

    private String stringProperty(Representation representation, String name, String fallback) {
        Object value = representation.properties().get(name);
        return value == null ? fallback : String.valueOf(value);
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
            }
        }
        return node;
    }
}
