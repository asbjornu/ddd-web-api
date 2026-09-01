package no.javazone.elevator.feature.viewstatus;

import java.util.Optional;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.security.Principal;
import no.javazone.elevator.shared.security.PrincipalResolver;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.RepresentationResponses;
import no.javazone.elevator.shared.web.UriResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /elevators/{id}}: the read side's own resource, replacing
 * the old {@code GET /elevators/{id}/status}. Queries never touch the
 * write-side aggregate -- this reads only {@link ElevatorViewProjection}
 * -- see {@code docs/architecture.md}'s "CQRS and domain events" section.
 *
 * <p>Affordances are computed from the read model's own {@code state}
 * string via {@link no.javazone.elevator.shared.hypermedia.AffordanceContext},
 * never from the write-side aggregate -- the query side has no reason
 * to depend on it. The same {@link Principal} every command resolves is
 * resolved here too: a technician's {@code GET} carries their own
 * operations, exactly as their next command response would.
 */
@RestController
public class ViewStatusController {

    private final ElevatorViewProjection projection;
    private final UriResolver uriResolver;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;
    private final PrincipalResolver principalResolver;
    private final ElevatorProperties properties;

    public ViewStatusController(
            ElevatorViewProjection projection,
            UriResolver uriResolver,
            AffordanceCatalog affordanceCatalog,
            RepresentationResponses responses,
            PrincipalResolver principalResolver,
            ElevatorProperties properties) {
        this.projection = projection;
        this.uriResolver = uriResolver;
        this.affordanceCatalog = affordanceCatalog;
        this.responses = responses;
        this.principalResolver = principalResolver;
        this.properties = properties;
    }

    @GetMapping("/elevators/{segment}")
    public ResponseEntity<String> view(
            @PathVariable String segment,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Optional<ElevatorId> id = resolve(segment);
        Optional<ElevatorView> view = id.flatMap(projection::find);
        if (view.isEmpty()) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        }
        Representation representation = ElevatorRepresentations.representation(
                segment, view.get(), affordanceCatalog, principalResolver.resolve(), properties);
        return responses.ok(accept, representation);
    }

    private Optional<ElevatorId> resolve(String segment) {
        try {
            return Optional.of(uriResolver.resolve(segment));
        } catch (RuntimeException invalidSegment) {
            return Optional.empty();
        }
    }
}
