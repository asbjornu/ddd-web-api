package no.javazone.elevator.shared.render;

import no.javazone.elevator.shared.hypermedia.Representation;

/**
 * Escaping and the {@code <dl>} markup shared by every renderer that
 * needs it -- see {@link HtmlRenderer}.
 */
final class HtmlFragments {

    private HtmlFragments() {
    }

    static String propertiesList(Representation representation) {
        StringBuilder html = new StringBuilder();
        html.append("<dl>\n");
        representation.properties().forEach((name, value) -> html
                .append("  <dt>").append(escape(name)).append("</dt>")
                .append("<dd>").append(escape(String.valueOf(value))).append("</dd>\n"));
        html.append("</dl>\n");
        return html.toString();
    }

    static String escape(String value) {
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
