package no.javazone.elevator.shared.hypermedia;

import java.util.List;

/**
 * A named operation the caller may legally invoke <em>right now</em>: a
 * command, made discoverable. Omission -- not a disabled affordance --
 * is how "not available" is expressed; see
 * {@code docs/architecture.md}'s "Affordances: hypermedia over the
 * aggregate" section.
 *
 * <p>{@code rel} is a dereferenceable URI (or a path resolved against the
 * rel-vocabulary base -- see {@code RelVocabulary}), never a bare word:
 * see {@code docs/plan.html} &sect;7's "Rel vocabulary".
 */
public record Affordance(
        String rel,
        String title,
        String method,
        String href,
        List<Field> fields) {

    public Affordance {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public Affordance(String rel, String title, String method, String href) {
        this(rel, title, method, href, List.of());
    }
}
