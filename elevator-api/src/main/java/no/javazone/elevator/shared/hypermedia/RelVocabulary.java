package no.javazone.elevator.shared.hypermedia;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Collects every {@link RelDocumentationSource} bean into one lookup, for
 * {@code RelsController} to serve at {@code /rels/{name}}.
 */
@Component
public class RelVocabulary {

    private final List<RelDefinition> rels;

    public RelVocabulary(List<RelDocumentationSource> sources) {
        this.rels = sources.stream()
                .flatMap(source -> source.rels().stream())
                .toList();
    }

    public Optional<RelDefinition> find(String name) {
        return rels.stream()
                .filter(rel -> rel.name().equals(name))
                .findFirst();
    }
}
