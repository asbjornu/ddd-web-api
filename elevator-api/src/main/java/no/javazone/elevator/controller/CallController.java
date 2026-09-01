package no.javazone.elevator.controller;

import java.util.List;
import no.javazone.elevator.model.Call;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing calls: a rider at a floor requesting the car. See "Call
 * elevator" in docs/architecture.md.
 */
@RestController
public class CallController {

    private final ElevatorService elevatorService;

    public CallController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @PostMapping("/elevators/{id}/calls")
    @ResponseStatus(HttpStatus.CREATED)
    public Call call(@PathVariable Long id, @RequestBody Call call) {
        return elevatorService.call(id, call);
    }

    @GetMapping("/elevators/{id}/calls")
    public List<Call> calls(@PathVariable Long id) {
        return elevatorService.listCalls(id);
    }
}
