package no.javazone.elevator.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * A plain HTML form has no way to attach an {@code Authorization}
 * header of its own; this pins down the one thing
 * {@link TechnicianCookieAuthenticationFilter} does about that: a
 * request carrying only the technician cookie reaches downstream
 * filters (Spring Security's own {@code BearerTokenAuthenticationFilter}
 * among them) as though it had presented that same value as a Bearer
 * header, a request that already has a real header is left alone, and
 * a request with neither is untouched.
 */
class TechnicianCookieAuthenticationFilterTest {

    private final TechnicianCookieAuthenticationFilter filter = new TechnicianCookieAuthenticationFilter();

    @Test
    void aCookieBecomesABearerHeaderDownstream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(TechnicianSessionCookie.NAME, "the-token-value"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenHeader = new String[1];
        filter.doFilter(request, response, (req, res) ->
                seenHeader[0] = ((jakarta.servlet.http.HttpServletRequest) req)
                        .getHeader(HttpHeaders.AUTHORIZATION));

        assertThat(seenHeader[0]).isEqualTo("Bearer the-token-value");
    }

    @Test
    void aRealHeaderIsLeftAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(TechnicianSessionCookie.NAME, "cookie-value"));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer real-header-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenHeader = new String[1];
        filter.doFilter(request, response, (req, res) ->
                seenHeader[0] = ((jakarta.servlet.http.HttpServletRequest) req)
                        .getHeader(HttpHeaders.AUTHORIZATION));

        assertThat(seenHeader[0]).isEqualTo("Bearer real-header-value");
    }

    @Test
    void neitherCookieNorHeaderMeansNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenHeader = new String[]{"unset"};
        filter.doFilter(request, response, (req, res) ->
                seenHeader[0] = ((jakarta.servlet.http.HttpServletRequest) req)
                        .getHeader(HttpHeaders.AUTHORIZATION));

        assertThat(seenHeader[0]).isNull();
    }
}
