package no.javazone.elevator.controller;

import java.util.List;
import no.javazone.elevator.model.Call;
import no.javazone.elevator.service.ElevatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing calls: read-only now. {@code POST /elevators/{id}/calls} moved
 * onto the new aggregate in slice 2 -- see
 * {@code no.javazone.elevator.feature.callelevator.CallElevatorController}
 * -- and this class kept only the listing endpoint, which several other
 * still-unmigrated behaviours (maintenance, emergency recall, the
 * movement scheduler) continue to read via {@link ElevatorService}.
 * Nothing is ever added to this list any more, so it will only ever
 * shrink to empty, never grow -- and is deleted outright once the
 * slice that removes those readers lands.
 */
@RestController
public class CallController {

    private final ElevatorService elevatorService;

    public CallController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    @GetMapping("/elevators/{id}/calls")
    public List<Call> calls(@PathVariable Long id) {
        return elevatorService.listCalls(id);
    }
}

