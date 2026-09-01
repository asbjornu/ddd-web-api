package no.javazone.elevator.controller;

import java.util.List;
import no.javazone.elevator.model.CarCall;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Car calls: a rider inside the car selecting a destination floor.
 * See "Select floor" in docs/architecture.md.
 */
@RestController
public class CarCallController {

    private final ElevatorService elevatorService;

    public CarCallController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @PostMapping("/elevators/{id}/car-calls")
    @ResponseStatus(HttpStatus.CREATED)
    public CarCall carCall(@PathVariable Long id, @RequestBody CarCall carCall) {
        return elevatorService.carCall(id, carCall);
    }

    @GetMapping("/elevators/{id}/car-calls")
    public List<CarCall> carCalls(@PathVariable Long id) {
        return elevatorService.listCarCalls(id);
    }
}
