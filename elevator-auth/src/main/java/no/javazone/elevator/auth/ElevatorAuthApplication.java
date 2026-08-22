package no.javazone.elevator.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Authorization server for the elevator control system.
 *
 * <p>It exists so that the technician's key-switch credential is exchanged
 * for a scoped, signed access token, rather than being compared against a
 * string inside {@code elevator-api}. It issues nothing else and knows
 * nothing about elevators.
 *
 * <p>There is no Java configuration: the registered client, its scopes and
 * the issuer are declared in {@code application.yml} and assembled by Spring
 * Boot's autoconfiguration. Keeping it that way is deliberate -- the whole
 * server should be readable in one screen of YAML.
 */
@SpringBootApplication
public class ElevatorAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElevatorAuthApplication.class, args);
    }
}
