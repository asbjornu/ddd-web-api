package no.javazone.elevator.feature.insertkey;

import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Exchanges the technician's typed key-switch secret for a scoped
 * access token at {@code elevator-auth}, the one call in this API that
 * plays OAuth2 client rather than resource server. Only {@code
 * elevator-api} can make this call at all: {@code elevator-auth} has no
 * published port and is not one of Caddy's two allowlisted prefixes
 * (see the root {@code Caddyfile}), so a browser can never reach it
 * directly -- see {@code docs/architecture.md}'s "Key-switch and
 * authorization" section.
 *
 * <p>The client id is public (it names the one registered OAuth client,
 * documented in {@code elevator-auth}'s own configuration); nothing
 * secret is held here. What the technician types is the client secret,
 * per that same configuration's own comment.
 */
@Component
class TechnicianTokenExchange {

    /** What {@code elevator-auth} answers a successful {@code client_credentials}
     * grant with -- only the fields this API needs from it. */
    record Token(String accessToken, String scope, long expiresInSeconds) {
    }

    private final RestClient restClient;
    private final String issuer;
    private final String clientId;

    TechnicianTokenExchange(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${elevator.technician.client-id:elevator-technician}") String clientId) {
        this.restClient = RestClient.create();
        this.issuer = issuer;
        this.clientId = clientId;
    }

    /** Returns the token, or empty if {@code elevator-auth} refused the
     * secret (a wrong key) or could not be reached at all -- both mean
     * the same thing to the technician: the key did not turn. */
    Optional<Token> exchange(String secret) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "elevator:maintenance elevator:recall");
        try {
            Map<String, Object> response = restClient.post()
                    .uri(issuer + "/oauth2/token")
                    .headers(headers -> headers.setBasicAuth(clientId, secret))
                    .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (response == null || response.get("access_token") == null) {
                return Optional.empty();
            }
            return Optional.of(new Token(
                    String.valueOf(response.get("access_token")),
                    String.valueOf(response.get("scope")),
                    ((Number) response.get("expires_in")).longValue()));
        } catch (RestClientException refusedOrUnreachable) {
            return Optional.empty();
        }
    }
}
