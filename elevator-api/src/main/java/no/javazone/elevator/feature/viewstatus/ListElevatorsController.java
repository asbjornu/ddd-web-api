package no.javazone.elevator.feature.viewstatus;

import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.web.RepresentationResponses;
import no.javazone.elevator.shared.web.UriResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /elevators}: the collection a client follows -- from
 * {@link no.javazone.elevator.shared.web.EntryPointController}'s own
 * {@code elevators} link -- to learn which elevators exist, rather
 * than being told one. Only one {@code elevator} link is seeded today,
 * but nothing on the client side may assume that stays true -- see
 * {@code AGENTS.md}: "the API is shaped to support multiple
 * elevators... but only one elevator is seeded and used for now".
 *
 * <p>Discovery is two steps, not one: {@code GET /} names this
 * collection, this collection names each elevator. Folding both
 * relations onto the entry point directly would leave a client no way
 * to distinguish "the building's elevators" from any other kind of
 * link the entry point might one day carry.
 */
@RestController
public class ListElevatorsController {

    private final ElevatorViewProjection projection;
    private final UriResolver uriResolver;
    private final RepresentationResponses responses;

    public ListElevatorsController(
            ElevatorViewProjection projection, UriResolver uriResolver, RepresentationResponses responses) {
        this.projection = projection;
        this.uriResolver = uriResolver;
        this.responses = responses;
    }

    @GetMapping("/elevators")
    public ResponseEntity<String> list(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Representation.Builder builder = Representation.builder("Elevators")
                .containerId("elevators-collection");
        String firstElevatorHref = null;
        for (ElevatorId id : projection.findAllIds()) {
            String href = "/elevators/" + uriResolver.segmentFor(id);
            builder.link(new Link("elevator", href));
            if (firstElevatorHref == null) {
                firstElevatorHref = href;
            }
        }
        // This client always follows the first elevator; a future
        // multi-elevator building would need its own way to choose,
        // which is not this slice's job.
        if (firstElevatorHref != null) {
            builder.autoInit("elevator", firstElevatorHref);
        }
        return responses.ok(accept, builder.build());
    }
}
