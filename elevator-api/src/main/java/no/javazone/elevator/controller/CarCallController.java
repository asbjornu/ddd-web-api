package no.javazone.elevator.controller;

import java.util.List;
import no.javazone.elevator.model.CarCall;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Car calls: read-only now. {@code POST /elevators/{id}/car-calls}
 * moved onto the new aggregate in slice 3 -- see
 * {@code no.javazone.elevator.feature.selectfloor.SelectFloorController}
 * -- and this class kept only the listing endpoint, which doors,
 * weight and maintenance/recall characterisation tests still read via
 * {@link ElevatorService}. Deleted outright once those migrate too.
 */
@RestController
public class CarCallController {

    private final ElevatorService elevatorService;

    public CarCallController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @GetMapping("/elevators/{id}/car-calls")
    public List<CarCall> carCalls(@PathVariable Long id) {
        return elevatorService.listCarCalls(id);
    }
}
