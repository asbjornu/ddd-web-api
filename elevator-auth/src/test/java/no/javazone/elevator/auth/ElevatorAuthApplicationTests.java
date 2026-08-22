package no.javazone.elevator.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@SpringBootTest
class ElevatorAuthApplicationTests {

    @Autowired
    private RegisteredClientRepository clients;

    @Test
    void registersTheTechnicianClientWithBothScopes() {
        RegisteredClient client = clients.findByClientId("elevator-technician");

        assertThat(client).isNotNull();
        assertThat(client.getScopes())
                .containsExactlyInAnyOrder("elevator:maintenance", "elevator:recall");
    }

    @Test
    void issuesOnlyClientCredentialsTokens() {
        RegisteredClient client = clients.findByClientId("elevator-technician");

        assertThat(client.getAuthorizationGrantTypes())
                .singleElement()
                .satisfies(grant -> assertThat(grant.getValue()).isEqualTo("client_credentials"));
    }
}
