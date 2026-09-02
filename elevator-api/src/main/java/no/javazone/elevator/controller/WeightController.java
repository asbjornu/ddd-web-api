package no.javazone.elevator.controller;

import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulated weight sensor: the rider sets a weight via a UI slider.
 * Over-capacity triggers overload behaviour (hold doors, clear car
 * calls, refuse to move). See "Doors and overload" in
 * docs/architecture.md.
 */
@RestController
public class WeightController {

    private final ElevatorService elevatorService;

    public WeightController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @PutMapping("/elevators/{id}/weight")
    public Elevator setWeight(@PathVariable Long id, @RequestBody WeightRequest body) {
        return elevatorService.setWeight(id, body.weightKg());
    }
}
