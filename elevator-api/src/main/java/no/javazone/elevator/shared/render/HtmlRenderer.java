package no.javazone.elevator.shared.render;

import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.Field;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * A server-rendered HTML page: forms for affordances, anchors for
 * links. HTML forms support only {@code GET} and {@code POST}, so a
 * {@code PUT}/{@code PATCH}/{@code DELETE} affordance carries a
 * {@code data-method} attribute -- the missing "LI" H-Factor, patched
 * over by a JavaScript library at the front end -- see
 * {@code docs/plan.html} &sect;7's "Scoring the formats".
 *
 * <p>Plain string building rather than a template engine for now: the
 * choice of JTE vs. Thymeleaf is explicitly "to verify in slice 0" per
 * {@code docs/plan.html} &sect;12 and is deferred to the slice that
 * needs real page composition (status/SSE, slice 1), since this
 * renderer has only the entry point to render today.
 */
@Component
public class HtmlRenderer implements Renderer {

    @Override
    public MediaType mediaType() {
        return MediaType.TEXT_HTML;
    }

    @Override
    public String render(Representation representation) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>").append(escape(representation.title())).append("</title></head><body>\n")
                .append("<h1>").append(escape(representation.title())).append("</h1>\n");

        if (!representation.properties().isEmpty()) {
            html.append("<dl>\n");
            representation.properties().forEach((name, value) -> html
                    .append("  <dt>").append(escape(name)).append("</dt>")
                    .append("<dd>").append(escape(String.valueOf(value))).append("</dd>\n"));
            html.append("</dl>\n");
        }

        if (!representation.links().isEmpty()) {
            html.append("<ul>\n");
            for (Link link : representation.links()) {
                html.append("  <li><a rel=\"").append(escape(link.rel())).append("\" href=\"")
                        .append(escape(link.href())).append("\">").append(escape(link.rel()))
                        .append("</a></li>\n");
            }
            html.append("</ul>\n");
        }

        for (Affordance affordance : representation.affordances()) {
            html.append(form(affordance));
        }

        html.append("</body></html>\n");
        return html.toString();
    }

    private String form(Affordance affordance) {
        boolean htmlNativeMethod = "GET".equalsIgnoreCase(affordance.method())
                || "POST".equalsIgnoreCase(affordance.method());
        String formMethod = htmlNativeMethod ? affordance.method() : "POST";

        StringBuilder form = new StringBuilder();
        form.append("<form action=\"").append(escape(affordance.href())).append("\" method=\"")
                .append(formMethod.toLowerCase(java.util.Locale.ROOT)).append("\"")
                .append(" data-rel=\"").append(escape(affordance.rel())).append("\"");
        if (!htmlNativeMethod) {
            form.append(" data-method=\"").append(affordance.method()).append("\"");
        }
        form.append(">\n  <fieldset>\n  <legend>").append(escape(affordance.title()))
                .append("</legend>\n");
        for (Field field : affordance.fields()) {
            form.append("  <label>").append(escape(field.name())).append("\n");
            if ("select".equals(field.type())) {
                form.append("    <select name=\"").append(escape(field.name())).append("\">\n");
                for (String option : field.options()) {
                    boolean selected = option.equals(String.valueOf(field.value()));
                    form.append("      <option value=\"").append(escape(option)).append("\"")
                            .append(selected ? " selected" : "").append(">").append(escape(option))
                            .append("</option>\n");
                }
                form.append("    </select>\n");
            } else {
                form.append("    <input type=\"").append(escape(field.type())).append("\" name=\"")
                        .append(escape(field.name())).append("\" value=\"")
                        .append(escape(String.valueOf(field.value()))).append("\"")
                        .append(field.required() ? " required" : "").append(">\n");
            }
            form.append("  </label>\n");
        }
        form.append("  <button type=\"submit\">").append(escape(affordance.title()))
                .append("</button>\n  </fieldset>\n</form>\n");
        return form.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
