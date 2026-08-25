package no.javazone.elevator.shared.security;

import java.util.Set;

/**
 * The one thing any handler or {@code AffordanceContributor} ever sees
 * once a caller's credential has been validated -- exchanged, once, at
 * the security boundary ({@code CommandsController}), for this type,
 * per Bergh Johnsson &amp; Deogun's "validate once at the border, let
 * the type carry the proof" (see {@code docs/architecture.md}'s
 * "Key-switch and authorization" section). No handler re-parses a scope
 * string or re-checks for a missing principal: {@link #ANONYMOUS} is
 * what an unauthenticated caller carries, never {@code null}.
 *
 * <p>Deliberately not called {@code User} or {@code Technician}: this
 * is a browser session and a machine client's Bearer token converging
 * on one shape, not an identity -- see {@code docs/plan.html}'s "Two
 * client paths, one authorization model".
 */
public record Principal(Set<String> scopes) {

    public static final Principal ANONYMOUS = new Principal(Set.of());

    public Principal {
        scopes = Set.copyOf(scopes);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    public boolean hasAnyScope() {
        return !scopes.isEmpty();
    }
}
