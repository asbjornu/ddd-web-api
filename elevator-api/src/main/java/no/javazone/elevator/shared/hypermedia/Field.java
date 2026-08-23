package no.javazone.elevator.shared.hypermedia;

import java.util.List;

/**
 * One field of an {@link Affordance}'s payload: a name, an HTML5 input
 * type (or {@code "select"} for a permitted-values list, the community
 * extension {@code docs/plan.html} &sect;9 documents), a current or
 * default value, whether it is required, and -- for {@code select} --
 * the permitted options.
 *
 * <p>Carrying a default here is what lets a server introduce a new
 * required field without breaking a client that has never heard of it:
 * see {@code docs/architecture.md}'s "No versioning" section.
 */
public record Field(
        String name,
        String type,
        Object value,
        boolean required,
        List<String> options) {

    public Field {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static Field text(String name, Object value) {
        return new Field(name, "text", value, true, null);
    }

    public static Field hidden(String name, Object value) {
        return new Field(name, "hidden", value, true, null);
    }

    public static Field select(String name, Object value, List<String> options) {
        return new Field(name, "select", value, true, options);
    }
}
