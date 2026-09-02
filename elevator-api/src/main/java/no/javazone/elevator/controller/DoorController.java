package no.javazone.elevator.controller;

import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Door actions: explicit open/close and obstruction simulation.
 * See "Doors" in docs/architecture.md.
 */
@RestController
public class DoorController {

    private final ElevatorService elevatorService;

    public DoorController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @PostMapping("/elevators/{id}/open-doors")
    public Elevator openDoors(@PathVariable Long id) {
        return elevatorService.openDoors(id);
    }

    @PostMapping("/elevators/{id}/close-doors")
    public Elevator closeDoors(@PathVariable Long id) {
        return elevatorService.closeDoors(id);
    }

    @PutMapping("/elevators/{id}/obstruction")
    public Elevator setObstruction(@PathVariable Long id, @RequestBody ObstructionRequest body) {
        return elevatorService.setObstruction(id, body.obstructed());
    }
}
