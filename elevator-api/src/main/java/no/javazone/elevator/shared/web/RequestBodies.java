package no.javazone.elevator.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads a request body as {@link JsonNode}, whether it arrived as JSON
 * or {@code application/x-www-form-urlencoded} -- every affordance is
 * rendered as a plain HTML {@code <form>} (see {@code
 * no.javazone.elevator.shared.render.HtmlRenderer}), and a form
 * submitted the way Datastar's {@code contentType: 'form'} option (or a
 * browser with JavaScript disabled entirely) sends one arrives
 * form-encoded, not as JSON. Shared by {@link CommandsController} and
 * {@code KeySwitchSessionController} -- the two endpoints a rendered
 * form ever posts to -- so neither repeats the same normalisation.
 */
public final class RequestBodies {

    private RequestBodies() {
    }

    /** Form values arrive as plain strings; a field a command expects as
     * a number (e.g. {@code floor}, {@code weightKg}) needs a genuine
     * numeric node -- {@code JsonNode.canConvertToInt()} is {@code
     * false} for a text node no matter what it contains -- so this
     * parses opportunistically rather than leaving every form field a
     * string the same field's JSON shape never would be. */
    public static JsonNode read(HttpServletRequest request, ObjectMapper objectMapper) {
        String contentType = request.getContentType();
        if (contentType != null
                && contentType.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            ObjectNode node = objectMapper.createObjectNode();
            request.getParameterMap().forEach((name, values) -> {
                if (values.length > 0) {
                    putField(node, name, values[0]);
                }
            });
            return node;
        }
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            return bytes.length == 0 ? null : objectMapper.readTree(bytes);
        } catch (IOException failedToRead) {
            throw new UncheckedIOException(failedToRead);
        }
    }

    private static void putField(ObjectNode node, String name, String value) {
        try {
            node.put(name, Integer.parseInt(value));
        } catch (NumberFormatException notAnInteger) {
            node.put(name, value);
        }
    }
}
