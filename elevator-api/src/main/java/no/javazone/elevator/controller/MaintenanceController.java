package no.javazone.elevator.controller;

import java.util.Map;
import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Technician key-switch actions: enter/exit maintenance and emergency
 * recall.
 *
 * <p>Authorisation is no longer this controller's business. The scopes
 * required are declared once in
 * {@link no.javazone.elevator.config.SecurityConfig}, and a request that
 * arrives here has already presented a valid token carrying them. See
 * "Authentication and authorization" in docs/architecture.md.
 */
@RestController
public class MaintenanceController {

    private final ElevatorService elevatorService;

    public MaintenanceController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @PostMapping("/elevators/{id}/maintenance")
    public Elevator maintenance(@PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean enable = body.getOrDefault("maintenance", false);
        if (enable) {
            return elevatorService.enterMaintenance(id);
        } else {
            return elevatorService.exitMaintenance(id);
        }
    }

    @PostMapping("/elevators/{id}/emergency-recall")
    public Elevator emergencyRecall(@PathVariable Long id) {
        return elevatorService.triggerEmergencyRecall(id);
    }
}
