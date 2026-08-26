package no.javazone.elevator.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets a browser's plain HTML form -- which cannot attach an {@code
 * Authorization} header of its own -- carry a technician's token
 * anyway: if a request has no {@code Authorization} header but does
 * carry the {@link TechnicianSessionCookie}, this wraps the request so
 * Spring Security's own {@code BearerTokenAuthenticationFilter} (which
 * runs immediately after, see {@code SecurityConfig}) sees the cookie's
 * value as though it had arrived as a normal Bearer token. A machine
 * client presenting its own {@code Authorization} header is untouched
 * -- the cookie is only ever a fallback for the one client that cannot
 * set headers.
 */
public class TechnicianCookieAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            chain.doFilter(request, response);
            return;
        }
        Optional<String> token = cookieValue(request);
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new BearerTokenFromCookie(request, token.get()), response);
    }

    private Optional<String> cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> TechnicianSessionCookie.NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private static final class BearerTokenFromCookie extends HttpServletRequestWrapper {

        private final String token;

        private BearerTokenFromCookie(HttpServletRequest request, String token) {
            super(request);
            this.token = token;
        }

        @Override
        public String getHeader(String name) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                return "Bearer " + token;
            }
            return super.getHeader(name);
        }
    }
}
