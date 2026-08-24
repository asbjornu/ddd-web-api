package no.javazone.elevator.feature.obstructdoors;

import tools.jackson.databind.JsonNode;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.render.ElevatorStateJsonRenderer;
import no.javazone.elevator.shared.web.CommandEndpoint;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.RepresentationResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Answers {@code "type": "ObstructDoors"} on {@code POST /elevators/{id}}:
 * the simulated light curtain, replacing the old
 * {@code POST /elevators/{id}/obstruction} (with its boolean body doing
 * double duty for both obstruct and clear) with two distinct commands
 * -- see this slice's sibling package, {@code feature.clearobstruction},
 * for the other half.
 */
@Component
public class ObstructDoorsController implements CommandEndpoint {

    private final ObstructDoorsHandler handler;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer eventRenderer;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;

    public ObstructDoorsController(
            ObstructDoorsHandler handler,
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
        return "ObstructDoors";
    }

    @Override
    public ResponseEntity<String> handle(ElevatorId id, String segment, JsonNode body, String accept) {
        try {
            handler.handle(new ObstructDoorsCommand(id));
        } catch (ObstructDoorsHandler.UnknownElevator unknown) {
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
}
