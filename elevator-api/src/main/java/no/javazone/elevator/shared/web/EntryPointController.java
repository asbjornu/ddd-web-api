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
 * {@code GET /}: this API's own hypermedia entry point -- and the one
 * URL a client is ever configured with. Discovery from here is two
 * steps, not one: an {@code elevators} link names the collection
 * ({@link no.javazone.elevator.feature.viewstatus.ListElevatorsController}),
 * which in turn names each known elevator. Folding "which elevators
 * exist" onto the entry point directly would leave a client no way to
 * tell that relation apart from any other kind of link the entry point
 * might one day carry.
 *
 * <p>Reached by machine clients addressing {@code elevator-api} on its
 * own network directly, and by {@code elevator-ui}'s own client-side
 * code through Caddy's shared origin (see the root {@code Caddyfile}):
 * the same path, content-negotiated -- a browser navigating here gets
 * {@code elevator-ui}'s page (Caddy routes plain {@code text/html} Accept
 * headers there instead, per {@code docs/plan.html} &sect;19's "Keeping
 * Nuxt" decision), while a request naming one of this API's own media
 * types reaches this controller either way.
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
                .link(new Link("elevators", "/elevators"))
                .affordances(affordanceCatalog.affordances(AffordanceContext.root()))
                .containerId("entry-point")
                .autoInit("elevators-collection", "/elevators")
                .build();
        return responses.ok(accept, representation);
    }
}
