package no.javazone.elevator.controller;

import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated read model. No technician key is required
 * here -- see "Authentication and authorization" in
 * docs/architecture.md.
 */
@RestController
public class ElevatorStatusController {

    private final ElevatorService elevatorService;

    public ElevatorStatusController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @GetMapping("/elevators/{id}/status")
    public Elevator status(@PathVariable Long id) {
        return elevatorService.getStatus(id);
    }
}
