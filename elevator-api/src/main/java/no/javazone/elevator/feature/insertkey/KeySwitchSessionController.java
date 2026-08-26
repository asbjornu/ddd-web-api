package no.javazone.elevator.feature.insertkey;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.hypermedia.Representation;
import no.javazone.elevator.shared.security.Principal;
import no.javazone.elevator.shared.security.TechnicianSessionCookie;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.RepresentationResponses;
import no.javazone.elevator.shared.web.RequestBodies;
import no.javazone.elevator.shared.web.UriResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Completes the authorization exchange {@code insert-key} starts:
 * {@code POST /elevators/{id}/key-switch/session} takes the secret a
 * technician typed, exchanges it for a scoped access token at {@code
 * elevator-auth} ({@link TechnicianTokenExchange}), and mirrors that
 * token into an {@code HttpOnly} cookie -- so a plain HTML form
 * submitted afterwards (which cannot attach an {@code Authorization}
 * header of its own) is still recognised, via {@link
 * no.javazone.elevator.shared.security.TechnicianCookieAuthenticationFilter}.
 *
 * <p>This has to be {@code elevator-api}, not the browser, making that
 * call: {@code elevator-auth} has no published port and is reachable
 * only on the Docker network -- see {@code TechnicianTokenExchange}'s
 * Javadoc and the root {@code Caddyfile}. Every request after this one
 * goes back to being an ordinary Bearer token, validated the ordinary
 * way, whether it arrives via the cookie or (for a machine client) a
 * header of its own.
 *
 * <p>{@code DELETE} withdraws the key: it clears the cookie
 * unconditionally. There is no server-side session to invalidate --
 * the token remains technically valid until it expires, which is why
 * its lifetime is short.
 */
@RestController
public class KeySwitchSessionController {

    private final UriResolver uriResolver;
    private final ElevatorViewProjection projection;
    private final AffordanceCatalog affordanceCatalog;
    private final ElevatorProperties properties;
    private final TechnicianTokenExchange tokenExchange;
    private final RepresentationResponses responses;
    private final ObjectMapper objectMapper;

    public KeySwitchSessionController(
            UriResolver uriResolver,
            ElevatorViewProjection projection,
            AffordanceCatalog affordanceCatalog,
            ElevatorProperties properties,
            TechnicianTokenExchange tokenExchange,
            RepresentationResponses responses,
            ObjectMapper objectMapper) {
        this.uriResolver = uriResolver;
        this.projection = projection;
        this.affordanceCatalog = affordanceCatalog;
        this.properties = properties;
        this.tokenExchange = tokenExchange;
        this.responses = responses;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/elevators/{segment}/key-switch/session")
    public ResponseEntity<String> establish(
            @PathVariable String segment,
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Optional<ElevatorView> view = resolve(segment).flatMap(projection::find);
        if (view.isEmpty()) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        }

        JsonNode body = RequestBodies.read(request, objectMapper);
        String secret = body == null || !body.hasNonNull("secret") ? "" : body.get("secret").asText("");
        if (secret.isBlank()) {
            return responses.challenge(
                    HttpStatus.UNAUTHORIZED, accept, KeySwitchChallenge.representation(),
                    KeySwitchChallenge.WWW_AUTHENTICATE);
        }

        Optional<TechnicianTokenExchange.Token> token = tokenExchange.exchange(secret);
        if (token.isEmpty()) {
            return responses.problem(
                    HttpStatus.UNAUTHORIZED, accept, KeySwitchChallenge.representation());
        }

        Principal principal = new Principal(scopesOf(token.get()));
        Representation representation = ElevatorRepresentations.representation(
                segment, view.get(), affordanceCatalog, principal, properties);
        return withCookie(
                cookie(token.get().accessToken(), Duration.ofSeconds(token.get().expiresInSeconds())),
                responses.ok(accept, representation));
    }

    @DeleteMapping("/elevators/{segment}/key-switch/session")
    public ResponseEntity<String> withdraw(
            @PathVariable String segment,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Optional<ElevatorView> view = resolve(segment).flatMap(projection::find);
        if (view.isEmpty()) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        }

        Representation anonymousView = ElevatorRepresentations.representation(
                segment, view.get(), affordanceCatalog, Principal.ANONYMOUS, properties);
        return withCookie(cookie("", Duration.ZERO), responses.ok(accept, anonymousView));
    }

    private Set<String> scopesOf(TechnicianTokenExchange.Token token) {
        if (token.scope() == null || token.scope().isBlank()) {
            return Set.of();
        }
        return Set.copyOf(Arrays.asList(token.scope().split(" ")));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(TechnicianSessionCookie.NAME, value)
                .httpOnly(true)
                .sameSite("Strict")
                .path(TechnicianSessionCookie.PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseEntity<String> withCookie(ResponseCookie cookie, ResponseEntity<String> response) {
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response.getBody());
    }

    private Optional<ElevatorId> resolve(String segment) {
        try {
            return Optional.of(uriResolver.resolve(segment));
        } catch (RuntimeException invalidSegment) {
            return Optional.empty();
        }
    }
}
