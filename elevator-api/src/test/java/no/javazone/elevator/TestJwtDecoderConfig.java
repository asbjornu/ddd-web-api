package no.javazone.elevator;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Supplies the {@link JwtDecoder} the resource server needs to start,
 * without contacting an issuer.
 *
 * <p>Tests establish authority with
 * {@code SecurityMockMvcRequestPostProcessors.jwt()}, which injects the
 * authentication directly, so this decoder is never actually invoked. It
 * exists only so the security filter chain can be built.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException(
                    "Tests must use the jwt() request post-processor, not real tokens");
        };
    }
}
