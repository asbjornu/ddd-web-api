package no.javazone.elevator.shared.web;

import no.javazone.elevator.shared.hypermedia.RelDefinition;
import no.javazone.elevator.shared.hypermedia.RelVocabulary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /rels/{name}}: documentation for one rel, in plain HTML.
 * Rels are dereferenceable URIs; this is what dereferencing one returns
 * -- see {@code docs/plan.html} &sect;7's "Rel vocabulary".
 */
@RestController
public class RelsController {

    private final RelVocabulary relVocabulary;

    public RelsController(RelVocabulary relVocabulary) {
        this.relVocabulary = relVocabulary;
    }

    @GetMapping(value = "/rels/{name}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> rel(@PathVariable String name) {
        return relVocabulary.find(name)
                .map(this::page)
                .map(body -> ResponseEntity.ok().body(body))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(page(new RelDefinition(name, "No rel by this name is documented."))));
    }

    private String page(RelDefinition rel) {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <title>rel: %s</title></head><body>
                <h1>%s</h1>
                <p>%s</p>
                </body></html>
                """.formatted(escape(rel.name()), escape(rel.name()), escape(rel.description()));
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
