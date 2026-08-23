package no.javazone.elevator.feature.callelevator;

import tools.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Optional;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.Direction;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.domain.Floor;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.render.ElevatorStateJsonRenderer;
import no.javazone.elevator.shared.web.CommandEndpoint;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.RepresentationResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Answers {@code "type": "CallElevator"} on {@code POST /elevators/{id}}:
 * the first command, replacing the old field-assignment endpoint with a
 * named behaviour -- see {@code docs/architecture.md}'s "Core workflows,
 * as commands" section.
 *
 * <p>Returns the elevator's new representation on success, per
 * {@code docs/plan.html} &sect;10: "commands return the new
 * representation... including its now-different affordance set" --
 * though for {@code call-elevator} specifically, nothing displayed
 * changes yet (see {@code docs/architecture.md}'s slice 2 roadmap
 * entry), since scheduling the car to actually serve the call is
 * slice 3's job.
 */
@Component
public class CallElevatorController implements CommandEndpoint {

    private final CallElevatorHandler handler;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer eventRenderer;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;

    public CallElevatorController(
            CallElevatorHandler handler,
            ElevatorViewProjection projection,
            ElevatorViewUpdates updates,
            ElevatorStateJsonRenderer eventRenderer,
            AffordanceCatalog affordanceCatalog,
            RepresentationResponses responses) {
        this.handler = handler;
        this.projection = projection;
        this.updates = updates;
        this.eventRenderer = eventRenderer;
        this.affordanceCatalog = affordanceCatalog;
        this.responses = responses;
    }

    @Override
    public String type() {
        return "CallElevator";
    }

    @Override
    public ResponseEntity<String> handle(ElevatorId id, String segment, JsonNode body, String accept) {
        Optional<Floor> floor = parseFloor(body);
        Optional<Direction> direction = parseDirection(body);
        if (floor.isEmpty() || direction.isEmpty()) {
            return responses.problem(
                    HttpStatus.BAD_REQUEST,
                    accept,
                    ElevatorRepresentations.badRequest("A call needs an integer \"floor\" and a "
                            + "\"direction\" of \"up\" or \"down\"."));
        }

        try {
            handler.handle(new CallElevatorCommand(id, floor.get(), direction.get()));
        } catch (CallElevatorHandler.UnknownElevator unknown) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        } catch (CommandRefused refused) {
            return responses.problem(
                    HttpStatus.CONFLICT,
                    accept,
                    ElevatorRepresentations.conflict(segment, refused.getMessage()));
        }

        ElevatorView view = projection.find(id).orElseThrow();
        updates.publish(id, eventRenderer.render(ElevatorRepresentations.eventRepresentation(view)));
        return responses.ok(accept, ElevatorRepresentations.representation(segment, view, affordanceCatalog));
    }

    private Optional<Floor> parseFloor(JsonNode body) {
        if (body == null || !body.hasNonNull("floor") || !body.get("floor").canConvertToInt()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Floor(body.get("floor").asInt()));
        } catch (IllegalArgumentException invalidFloor) {
            return Optional.empty();
        }
    }

    private Optional<Direction> parseDirection(JsonNode body) {
        if (body == null || !body.hasNonNull("direction")) {
            return Optional.empty();
        }
        try {
            Direction direction =
                    Direction.valueOf(body.get("direction").asText("").toUpperCase(Locale.ROOT));
            return direction == Direction.NONE ? Optional.empty() : Optional.of(direction);
        } catch (IllegalArgumentException invalidDirection) {
            return Optional.empty();
        }
    }
}
