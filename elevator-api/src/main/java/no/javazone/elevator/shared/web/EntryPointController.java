package no.javazone.elevator.shared.web;

import java.util.Optional;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.render.Renderer;
import no.javazone.elevator.shared.render.RendererRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /}: this API's own hypermedia entry point. Slice 0's only
 * behaviour-bearing endpoint -- no elevator resource exists yet (slice
 * 1 adds {@code GET /elevators/{id}}), so today this offers only
 * documentation links and whatever affordances {@link AffordanceCatalog}
 * has been given, which is none.
 *
 * <p>Reached directly by machine clients addressing {@code elevator-api}
 * on its own network; the browser-facing shared origin (the Caddy proxy
 * added in this slice) routes {@code /} to {@code elevator-ui} instead,
 * per {@code docs/plan.html} &sect;19's "Keeping Nuxt" decision -- this
 * entry point is the API's own root, not the site's.
 */
@RestController
public class EntryPointController {

    private final AffordanceCatalog affordanceCatalog;
    private final RendererRegistry rendererRegistry;

    public EntryPointController(
            AffordanceCatalog affordanceCatalog, RendererRegistry rendererRegistry) {
        this.affordanceCatalog = affordanceCatalog;
        this.rendererRegistry = rendererRegistry;
    }

    @GetMapping("/")
    public ResponseEntity<String> entryPoint(
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Representation representation = Representation.builder("Elevator API")
                .link(new Link("self", "/"))
                .link(new Link("help", "/rels/help"))
                .affordances(affordanceCatalog.affordances())
                .build();

        Optional<Renderer> renderer = rendererRegistry.select(accept);
        if (renderer.isEmpty()) {
            return notAcceptable(accept);
        }
        return respond(HttpStatus.OK, renderer.get(), representation);
    }

    private ResponseEntity<String> notAcceptable(String accept) {
        Representation problem = Representation.builder("Not Acceptable")
                .property("type", "about:blank")
                .property("title", "Not Acceptable")
                .property("status", 406)
                .property(
                        "detail",
                        "None of the requested media types in the Accept header are "
                                + "available from this resource.")
                .build();
        Renderer renderer = rendererRegistry.selectForFailure(accept);
        return respond(HttpStatus.NOT_ACCEPTABLE, renderer, problem);
    }

    private ResponseEntity<String> respond(
            HttpStatus status, Renderer renderer, Representation representation) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, renderer.mediaType().toString());
        for (Link link : representation.links()) {
            String header = link.type() == null
                    ? "<%s>; rel=\"%s\"".formatted(link.href(), link.rel())
                    : "<%s>; rel=\"%s\"; type=\"%s\"".formatted(
                            link.href(), link.rel(), link.type());
            response.header(HttpHeaders.LINK, header);
        }
        return response.body(renderer.render(representation));
    }
}
