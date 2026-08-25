package no.javazone.elevator.config;

import org.springframework.beans.factory.annotation.Value;
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
 *
 * <p>{@code enter}/{@code exit-maintenance} no longer have a URL pattern
 * of their own to gate here: both now answer the same shared {@code POST
 * /elevators/{id}} every command does, so a {@code hasAuthority} matcher
 * keyed on the path can no longer tell them apart from any other command.
 * Their scope requirement is enforced instead exactly where {@code
 * docs/architecture.md}'s "Key-switch and authorization" section puts
 * it: inside {@code EnterMaintenanceController}/{@code
 * ExitMaintenanceController} themselves, the same place a domain
 * refusal would be, using the {@code Principal} {@code CommandsController}
 * resolves for every command. {@code emergency-recall} keeps its own
 * matcher below only because it has not moved yet -- that is slice 7's
 * job.
 */
@Configuration
public class SecurityConfig {

    private final String issuer;

    public SecurityConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        this.issuer = issuer;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/elevators/*/emergency-recall")
                            .hasAuthority("SCOPE_elevator:recall")
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        // GET /.well-known/oauth-protected-resource, built in since
                        // Spring Security 7.1 -- what insert-key's 401 challenge
                        // points a client at, per docs/architecture.md's
                        // "Key-switch and authorization" section and
                        // docs/plan.html's worked challengeSample.
                        .protectedResourceMetadata(metadata -> metadata
                                .protectedResourceMetadataCustomizer(builder -> builder
                                        .authorizationServer(issuer)
                                        .scope("elevator:maintenance")
                                        .scope("elevator:recall"))))
                // A resource server has no session to protect, and CSRF
                // defends cookie-authenticated requests. There are none.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
