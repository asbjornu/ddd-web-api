package no.javazone.elevator.shared.web;

import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.hypermedia.AffordanceContext;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /}: this API's own hypermedia entry point. Offers only
 * documentation links and whatever affordances {@link AffordanceCatalog}
 * has been given -- none yet, since no slice has contributed one.
 *
 * <p>Reached directly by machine clients addressing {@code elevator-api}
 * on its own network; the browser-facing shared origin (the Caddy proxy
 * added in slice 0) routes {@code /} to {@code elevator-ui} instead, per
 * {@code docs/plan.html} &sect;19's "Keeping Nuxt" decision -- this entry
 * point is the API's own root, not the site's.
 */
@RestController
public class EntryPointController {

    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;

    public EntryPointController(
            AffordanceCatalog affordanceCatalog, RepresentationResponses responses) {
        this.affordanceCatalog = affordanceCatalog;
        this.responses = responses;
    }

    @GetMapping("/")
    public ResponseEntity<String> entryPoint(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Representation representation = Representation.builder("Elevator API")
                .link(new Link("self", "/"))
                .link(new Link("help", "/rels/help"))
                .affordances(affordanceCatalog.affordances(AffordanceContext.root()))
                .build();
        return responses.ok(accept, representation);
    }
}
