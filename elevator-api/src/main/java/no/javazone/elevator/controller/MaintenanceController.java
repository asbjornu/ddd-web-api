package no.javazone.elevator.controller;

import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Technician key-switch actions: enter/exit maintenance.
 * Gated by a shared secret passed via Authorization: Bearer header.
 * See "Authentication and authorization" in docs/architecture.md.
 */
@RestController
public class MaintenanceController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ElevatorService elevatorService;
    private final ElevatorProperties properties;

    public MaintenanceController(ElevatorService elevatorService,
            ElevatorProperties properties) {
        this.elevatorService = elevatorService;
        this.properties = properties;
    }

    @PostMapping("/elevators/{id}/maintenance")
    public Elevator maintenance(@PathVariable Long id,
            @RequestHeader("Authorization") String authorization,
            @RequestBody MaintenanceRequest body) {
        String token = extractToken(authorization);
        if (token == null || !properties.technicianKey().equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid technician key");
        }
        if (body.maintenance()) {
            return elevatorService.enterMaintenance(id);
        } else {
            return elevatorService.exitMaintenance(id);
        }
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}
