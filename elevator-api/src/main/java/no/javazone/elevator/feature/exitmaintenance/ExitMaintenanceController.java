package no.javazone.elevator.feature.exitmaintenance;

import tools.jackson.databind.JsonNode;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.render.ElevatorStateJsonRenderer;
import no.javazone.elevator.shared.security.Principal;
import no.javazone.elevator.shared.web.CommandEndpoint;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.RepresentationResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Answers {@code "type": "ExitMaintenance"} on {@code POST /elevators/{id}}
 * -- see {@link no.javazone.elevator.feature.entermaintenance.EnterMaintenanceController}
 * for the identical authorization shape.
 */
@Component
public class ExitMaintenanceController implements CommandEndpoint {

    private final ExitMaintenanceHandler handler;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer eventRenderer;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;
    private final ElevatorProperties properties;

    public ExitMaintenanceController(
            ExitMaintenanceHandler handler,
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
        return "ExitMaintenance";
    }

    @Override
    public ResponseEntity<String> handle(
            ElevatorId id, String segment, JsonNode body, String accept, Principal principal) {
        if (!principal.hasScope("elevator:maintenance")) {
            return responses.problem(
                    HttpStatus.FORBIDDEN,
                    accept,
                    ElevatorRepresentations.forbidden(
                            "This operation requires the technician key."));
        }

        try {
            handler.handle(new ExitMaintenanceCommand(id));
        } catch (ExitMaintenanceHandler.UnknownElevator unknown) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        } catch (CommandRefused refused) {
            return responses.problem(
                    HttpStatus.CONFLICT, accept, ElevatorRepresentations.conflict(segment, refused));
        }

        ElevatorView view = projection.find(id).orElseThrow();
        updates.publish(id, eventRenderer.render(ElevatorRepresentations.eventRepresentation(view, properties)));
        return responses.ok(
                accept, ElevatorRepresentations.representation(segment, view, affordanceCatalog, principal, properties));
    }
}
