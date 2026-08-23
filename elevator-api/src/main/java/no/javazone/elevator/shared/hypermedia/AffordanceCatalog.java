package no.javazone.elevator.shared.hypermedia;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The seam every slice plugs into: a Spring-injected
 * {@code List<AffordanceContributor>} that knows nothing about elevators
 * as a concept. Adding a directory (a vertical slice) adds an
 * affordance; nothing here is edited to make that happen -- see
 * {@code docs/architecture.md}'s "Vertical slices" section.
 */
@Component
public class AffordanceCatalog {

    private final List<AffordanceContributor> contributors;

    public AffordanceCatalog(List<AffordanceContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    public List<Affordance> affordances(AffordanceContext context) {
        return contributors.stream()
                .flatMap(contributor -> contributor.contribute(context).stream())
                .toList();
    }
}
