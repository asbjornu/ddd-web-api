package no.javazone.elevator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This service is an OAuth2 resource server.
 *
 * <p>The rider's operations are deliberately open: calling the lift,
 * selecting a floor, working the doors and the two simulated sensors need
 * no credential, because a rider has no account and a lift has no login.
 * See "Authentication and authorization" in docs/architecture.md, which
 * also records what that openness costs.
 *
 * <p>The technician's key-switch actions require a scoped access token
 * issued by elevator-auth. There is no shared secret here any more: the
 * token is validated by signature against the issuer's published keys, and
 * authority is carried by scope rather than by knowing a string.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/elevators/*/maintenance")
                            .hasAuthority("SCOPE_elevator:maintenance")
                        .requestMatchers("/elevators/*/emergency-recall")
                            .hasAuthority("SCOPE_elevator:recall")
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // A resource server has no session to protect, and CSRF
                // defends cookie-authenticated requests. There are none.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
