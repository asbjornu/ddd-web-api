package no.javazone.elevator.shared.hypermedia;

import java.util.List;

/**
 * Implemented once per slice that introduces a rel worth documenting,
 * the same seam {@link AffordanceContributor} uses for affordances --
 * see {@code docs/architecture.md}'s "Vertical slices" section for why
 * this repository prefers a contributed list over a registry to edit.
 */
public interface RelDocumentationSource {

    List<RelDefinition> rels();
}
