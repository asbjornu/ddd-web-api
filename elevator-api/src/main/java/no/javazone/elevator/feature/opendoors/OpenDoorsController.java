package no.javazone.elevator.feature.opendoors;

import tools.jackson.databind.JsonNode;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.render.ElevatorStateJsonRenderer;
import no.javazone.elevator.shared.web.CommandEndpoint;
import no.javazone.elevator.shared.security.Principal;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.RepresentationResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Answers {@code "type": "OpenDoors"} on {@code POST /elevators/{id}},
 * replacing the old {@code DoorController}'s mapping the same way slice 2
 * replaced {@code CallController}'s.
 */
@Component
public class OpenDoorsController implements CommandEndpoint {

    private final OpenDoorsHandler handler;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer eventRenderer;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;
    private final ElevatorProperties properties;

    public OpenDoorsController(
            OpenDoorsHandler handler,
            ElevatorViewProjection projection,
            ElevatorViewUpdates updates,
            ElevatorStateJsonRenderer eventRenderer,
            AffordanceCatalog affordanceCatalog,
            RepresentationResponses responses,
            ElevatorProperties properties) {
        this.handler = handler;
        this.projection = projection;
        this.updates = updates;
        this.eventRenderer = eventRenderer;
        this.affordanceCatalog = affordanceCatalog;
        this.responses = responses;
        this.properties = properties;
    }

    @Override
    public String type() {
        return "OpenDoors";
    }

    @Override
    public ResponseEntity<String> handle(
            ElevatorId id, String segment, JsonNode body, String accept, Principal principal) {
        try {
            handler.handle(new OpenDoorsCommand(id));
        } catch (OpenDoorsHandler.UnknownElevator unknown) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        } catch (CommandRefused refused) {
            return responses.problem(
                    HttpStatus.CONFLICT,
                    accept,
                    ElevatorRepresentations.conflict(segment, refused.getMessage()));
        }

        ElevatorView view = projection.find(id).orElseThrow();
        updates.publish(id, eventRenderer.render(ElevatorRepresentations.eventRepresentation(view, properties)));
        return responses.ok(accept, ElevatorRepresentations.representation(segment, view, affordanceCatalog, principal, properties));
    }
}
