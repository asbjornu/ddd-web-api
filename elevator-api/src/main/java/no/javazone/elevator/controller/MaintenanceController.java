package no.javazone.elevator.controller;

import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emergency recall only -- {@code enter}/{@code exitMaintenance} moved
 * onto {@code POST /elevators/{id}} in slice 6 (see
 * {@code feature.entermaintenance}/{@code feature.exitmaintenance});
 * {@code triggerEmergencyRecall} stays here awaiting slice 7, per
 * {@code docs/architecture.md}'s roadmap.
 *
 * <p>Authorisation for the recall mapping below is still
 * {@link no.javazone.elevator.config.SecurityConfig}'s business, not
 * this controller's -- unlike maintenance, which now enforces its own
 * scope requirement in {@code EnterMaintenanceController}/{@code
 * ExitMaintenanceController}, per {@code docs/architecture.md}'s
 * "Key-switch and authorization" section.
 */
@RestController
public class MaintenanceController {

    private final ElevatorService elevatorService;

    public MaintenanceController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @PostMapping("/elevators/{id}/emergency-recall")
    public Elevator emergencyRecall(@PathVariable Long id) {
        return elevatorService.triggerEmergencyRecall(id);
    }
}
