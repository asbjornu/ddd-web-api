package no.javazone.elevator;

import no.javazone.elevator.config.ElevatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ElevatorProperties.class)
public class ElevatorApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElevatorApiApplication.class, args);
    }
}
