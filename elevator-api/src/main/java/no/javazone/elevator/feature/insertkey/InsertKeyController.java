package no.javazone.elevator.feature.insertkey;

import java.util.Optional;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.persistence.ElevatorAggregateStore;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.RepresentationResponses;
import no.javazone.elevator.shared.web.UriResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /elevators/{id}/key-switch}: the affordance a client
 * follows not to change the elevator, but to be allowed to change more
 * of it -- see {@code docs/architecture.md}'s "Key-switch and
 * authorization" section: turning the key switch is a domain concept
 * that is not an aggregate command, so it does not go through {@code
 * CommandsController} the way every real command does. Reaching this
 * endpoint at all only makes sense for a caller who does not already
 * hold a scope -- one who does was never offered {@code insert-key} in
 * the first place (see {@link InsertKeyAffordanceContributor}) -- so
 * its only possible answer is the RFC 9728 challenge {@code
 * docs/plan.html}'s {@code challengeSample} shows: discover the issuer
 * from {@code resource_metadata} rather than being configured with it.
 */
@RestController
public class InsertKeyController {

    private final UriResolver uriResolver;
    private final ElevatorAggregateStore store;
    private final RepresentationResponses responses;

    public InsertKeyController(
            UriResolver uriResolver, ElevatorAggregateStore store, RepresentationResponses responses) {
        this.uriResolver = uriResolver;
        this.store = store;
        this.responses = responses;
    }

    @PostMapping("/elevators/{segment}/key-switch")
    public ResponseEntity<String> insertKey(
            @PathVariable String segment,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Optional<ElevatorId> id = resolve(segment);
        if (id.isEmpty() || store.find(id.get()).isEmpty()) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        }

        Representation challenge = Representation.builder("Unauthorized")
                .property("type", "about:blank")
                .property("title", "Unauthorized")
                .property("status", 401)
                .property("detail", "Insert the technician key to discover how to authenticate.")
                .build();
        return responses.challenge(
                HttpStatus.UNAUTHORIZED,
                accept,
                challenge,
                "Bearer resource_metadata=\"/.well-known/oauth-protected-resource\", "
                        + "scope=\"elevator:maintenance elevator:recall\"");
    }

    private Optional<ElevatorId> resolve(String segment) {
        try {
            return Optional.of(uriResolver.resolve(segment));
        } catch (RuntimeException invalidSegment) {
            return Optional.empty();
        }
    }
}
