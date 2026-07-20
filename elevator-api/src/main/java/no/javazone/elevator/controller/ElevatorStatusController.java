package no.javazone.elevator.controller;

import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.repository.ElevatorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public, unauthenticated read model. No technician key is required
 * here -- see "Authentication and authorization" in
 * docs/architecture.md.
 */
@RestController
public class ElevatorStatusController {

    private final ElevatorRepository elevatorRepository;

    public ElevatorStatusController(ElevatorRepository elevatorRepository) {
        this.elevatorRepository = elevatorRepository;
    }

    @GetMapping("/elevators/{id}/status")
    public Elevator status(@PathVariable Long id) {
        return elevatorRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
