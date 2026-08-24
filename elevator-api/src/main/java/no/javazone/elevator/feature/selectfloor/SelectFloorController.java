package no.javazone.elevator.feature.selectfloor;

import tools.jackson.databind.JsonNode;
import java.util.Optional;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.CommandRefused;
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
 * Answers {@code "type": "SelectFloor"} on {@code POST /elevators/{id}}
 * -- see
 * {@link no.javazone.elevator.feature.callelevator.CallElevatorController}
 * for the identical shape.
 */
@Component
public class SelectFloorController implements CommandEndpoint {

    private final SelectFloorHandler handler;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer eventRenderer;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;

    public SelectFloorController(
            SelectFloorHandler handler,
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
        return "SelectFloor";
    }

    @Override
    public ResponseEntity<String> handle(ElevatorId id, String segment, JsonNode body, String accept) {
        Optional<Floor> floor = parseFloor(body);
        if (floor.isEmpty()) {
            return responses.problem(
                    HttpStatus.BAD_REQUEST,
                    accept,
                    ElevatorRepresentations.badRequest("A floor selection needs an integer \"floor\"."));
        }

        try {
            handler.handle(new SelectFloorCommand(id, floor.get()));
        } catch (SelectFloorHandler.UnknownElevator unknown) {
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
}
