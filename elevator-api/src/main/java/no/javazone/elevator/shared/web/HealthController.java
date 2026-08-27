package no.javazone.elevator.shared.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /health}: infrastructure-only, not part of the hypermedia
 * API proper (no representation, no content negotiation) -- the one
 * thing docker-compose's own healthcheck for this service depends on.
 * Replaces the identically-shaped controller of the same name that
 * lived in the now-deleted {@code controller} package; nothing about
 * this endpoint's contract changed, only where it lives.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "UP";
    }
}
