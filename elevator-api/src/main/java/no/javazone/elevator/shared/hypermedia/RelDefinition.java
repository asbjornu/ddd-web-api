package no.javazone.elevator.shared.hypermedia;

/**
 * A documented rel: its name and a plain-language description of what
 * following it means. Rendered as a page at {@code /rels/{name}} --
 * rels are dereferenceable URIs, and a URI can document itself, per
 * {@code docs/plan.html} &sect;7's "Rel vocabulary".
 */
public record RelDefinition(String name, String description) {
}
