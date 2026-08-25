package no.javazone.elevator.shared.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the current caller's {@link Principal} from whatever Spring
 * Security has already validated -- a Bearer JWT, in this API's case,
 * whether presented directly by a machine client or forwarded by
 * {@code elevator-ui}'s BFF on a browser's behalf; either way, by the
 * time a request reaches {@code shared.web.CommandsController}, Spring
 * Security's resource-server filter has already done the one thing
 * this class must never repeat: verifying the signature. This is only
 * where the already-verified {@code SCOPE_*} authorities become a
 * domain type.
 */
@Component
public class PrincipalResolver {

    private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

    public Principal resolve() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Principal.ANONYMOUS;
        }
        Set<String> scopes = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(SCOPE_AUTHORITY_PREFIX))
                .map(authority -> authority.substring(SCOPE_AUTHORITY_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        return scopes.isEmpty() ? Principal.ANONYMOUS : new Principal(scopes);
    }
}
