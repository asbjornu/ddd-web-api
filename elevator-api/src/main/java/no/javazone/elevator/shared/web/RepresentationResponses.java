package no.javazone.elevator.shared.web;

import no.javazone.elevator.shared.hypermedia.Link;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.render.Renderer;
import no.javazone.elevator.shared.render.RendererRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * The negotiate-then-render-then-emit-Link-headers sequence every
 * endpoint that returns a {@link Representation} needs, factored out so
 * no controller repeats it. Not a per-slice concern -- like the
 * renderers themselves, this is part of the shared kernel.
 */
@Component
public class RepresentationResponses {

    private final RendererRegistry rendererRegistry;

    public RepresentationResponses(RendererRegistry rendererRegistry) {
        this.rendererRegistry = rendererRegistry;
    }

    /** Negotiates a success response, falling back to a 406 problem if nothing matches. */
    public ResponseEntity<String> ok(String accept, Representation representation) {
        return rendererRegistry.select(accept)
                .map(renderer -> respond(HttpStatus.OK, renderer, representation))
                .orElseGet(() -> notAcceptable(accept));
    }

    /** Renders {@code representation} as a problem, negotiating among all five formats. */
    public ResponseEntity<String> problem(
            HttpStatus status, String accept, Representation problem) {
        return respond(status, rendererRegistry.selectForFailure(accept), problem);
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
        return respond(HttpStatus.NOT_ACCEPTABLE, rendererRegistry.selectForFailure(accept), problem);
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
