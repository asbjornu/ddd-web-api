package no.javazone.elevator.controller;

import no.javazone.elevator.model.Elevator;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Obstruction simulation only now: {@code open-doors} and
 * {@code close-doors} moved onto the new aggregate in slice 4 -- see
 * {@code no.javazone.elevator.feature.opendoors.OpenDoorsController} and
 * {@code no.javazone.elevator.feature.closedoors.CloseDoorsController}.
 * {@code ElevatorService.openDoors/closeDoors} themselves stay, since
 * {@code ElevatorServiceTest} still calls them directly.
 */
@RestController
public class DoorController {

    private final ElevatorService elevatorService;

    public DoorController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @PutMapping("/elevators/{id}/obstruction")
    public Elevator setObstruction(@PathVariable Long id, @RequestBody ObstructionRequest body) {
        return elevatorService.setObstruction(id, body.obstructed());
    }
}
