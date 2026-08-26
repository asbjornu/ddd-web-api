package no.javazone.elevator.shared.render;

import java.util.Locale;
import no.javazone.elevator.shared.hypermedia.Affordance;
import no.javazone.elevator.shared.hypermedia.Field;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * A server-rendered HTML page: forms for affordances, anchors for
 * links, Datastar-driven throughout -- see {@code docs/architecture.md}'s
 * "elevator-ui: front-end only, no BFF" section. HTML forms support
 * only {@code GET} and {@code POST}, so a {@code PUT}/{@code PATCH}/
 * {@code DELETE} affordance carries a {@code data-method} attribute (a
 * no-JS reader's only clue) alongside a {@code data-on:submit} that
 * actually issues the real method via a Datastar backend action --
 * see {@code docs/plan.html} &sect;7's "Scoring the formats".
 *
 * <p>Three representations chain into each other by nested,
 * self-triggering {@code data-init} divs -- see {@link Representation}'s
 * own {@code containerId}/{@code contentWrapperId}/{@code autoInits}: the
 * entry point names the elevators collection, the collection names one
 * elevator, and the elevator names its own event stream. A client is
 * never configured with, or constructs, anything past {@code GET /}.
 *
 * <p>Never embeds Datastar's own {@code <script>} tag: every one of
 * these representations may be fetched by Datastar's own {@code @get}
 * partway through that chain, and a {@code <script>} re-appearing in
 * patched-in content re-initialises the whole runtime, silently
 * doubling every live SSE connection thereafter. {@code elevator-ui}
 * loads the script exactly once, itself -- see its own {@code
 * nuxt.config.ts}; a machine client reading this HTML directly gets a
 * plain, inert page (every affordance still a real {@code <form>}), not
 * the live one.
 */
@Component
public class HtmlRenderer implements Renderer {

    @Override
    public MediaType mediaType() {
        return MediaType.TEXT_HTML;
    }

    @Override
    public String render(Representation representation) {
        String body = pageBody(representation);
        if (isDatastarRequest()) {
            // Datastar's own selector/mode response headers (see
            // RepresentationResponses) tell it *where* to place this
            // response, but not *which part* of it to use -- the whole
            // body becomes the replacement content at that selector. A
            // full standalone document's own <html>/<head>/<h1> wrapper
            // would leak in as stray sibling elements at every hop of the
            // discovery chain (and every command response) instead of
            // being discarded, so a request Datastar itself issued gets
            // just the div(s) it asked for, nothing surrounding them.
            return body;
        }
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<title>").append(escape(representation.title())).append("</title>")
                .append("</head><body>\n")
                .append("<h1>").append(escape(representation.title())).append("</h1>\n")
                .append(body)
                .append("</body></html>\n");
        return html.toString();
    }

    /** True for a request Datastar's own {@code @get}/{@code @post} issued
     * (it always sends this header -- see
     * https://data-star.dev/reference/actions#get), as opposed to a plain
     * browser navigation or a machine client reading this HTML directly. */
    private boolean isDatastarRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return false;
        }
        return "true".equalsIgnoreCase(attrs.getRequest().getHeader("datastar-request"));
    }

    /** {@code containerId}'s wrapper and the {@code autoInits} that
     * follow it, around {@link #contentFragment}, with neither a title nor
     * a document wrapper of its own -- callers needing either add their
     * own, since not every caller does (see {@link #render} and {@link
     * #isDatastarRequest}). */
    private String pageBody(Representation representation) {
        StringBuilder html = new StringBuilder();
        String containerId = representation.containerId();
        if (containerId != null) {
            html.append("<div id=\"").append(escape(containerId)).append("\">\n");
        }
        html.append(contentFragment(representation));
        for (Representation.AutoInit autoInit : representation.autoInits()) {
            html.append("<div id=\"").append(escape(autoInit.id())).append("\" data-init=\"@get('")
                    .append(escape(autoInit.href())).append("')\"></div>\n");
        }
        if (containerId != null) {
            html.append("</div>\n");
        }
        return html.toString();
    }

    /** The properties/links/forms alone, wrapped in {@code
     * contentWrapperId} if the representation names one -- never the
     * {@code containerId} wrapper or the {@code autoInits}, since this
     * is also what a live SSE patch (or a command's own POST response)
     * replaces, and re-sending either of those would re-trigger them.
     * See {@link no.javazone.elevator.feature.streamevents.ElevatorViewUpdates}. */
    public String contentFragment(Representation representation) {
        StringBuilder html = new StringBuilder();
        String contentWrapperId = representation.contentWrapperId();
        if (contentWrapperId != null) {
            html.append("<div id=\"").append(escape(contentWrapperId)).append("\">\n");
        }
        if (!representation.properties().isEmpty()) {
            html.append(HtmlFragments.propertiesList(representation));
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
        if (contentWrapperId != null) {
            html.append("</div>\n");
        }
        return html.toString();
    }

    private String form(Affordance affordance) {
        boolean htmlNativeMethod = "GET".equalsIgnoreCase(affordance.method())
                || "POST".equalsIgnoreCase(affordance.method());
        String formMethod = htmlNativeMethod ? affordance.method() : "POST";
        String datastarAction = affordance.method().toLowerCase(Locale.ROOT);

        StringBuilder form = new StringBuilder();
        form.append("<form action=\"").append(escape(affordance.href())).append("\" method=\"")
                .append(formMethod.toLowerCase(Locale.ROOT)).append("\"")
                .append(" data-rel=\"").append(escape(affordance.rel())).append("\"")
                .append(" data-on:submit=\"@").append(datastarAction).append("('")
                .append(jsEscape(affordance.href())).append("', {contentType: 'form'})\"");
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
        return HtmlFragments.escape(value);
    }

    private String jsEscape(String value) {
        return escape(value).replace("'", "\\'");
    }
}
