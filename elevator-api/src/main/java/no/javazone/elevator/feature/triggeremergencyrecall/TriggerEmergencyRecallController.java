package no.javazone.elevator.feature.triggeremergencyrecall;

import tools.jackson.databind.JsonNode;
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
 * Answers {@code "type": "TriggerEmergencyRecall"} on {@code POST
 * /elevators/{id}} -- see {@code
 * no.javazone.elevator.feature.entermaintenance.EnterMaintenanceController}
 * for the identical authorization shape: missing {@code
 * elevator:recall} is a bare 403, checked inside the command itself,
 * not a security filter in front of it.
 */
@Component
public class TriggerEmergencyRecallController implements CommandEndpoint {

    private final TriggerEmergencyRecallHandler handler;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer eventRenderer;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;

    public TriggerEmergencyRecallController(
            TriggerEmergencyRecallHandler handler,
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
        return "TriggerEmergencyRecall";
    }

    @Override
    public ResponseEntity<String> handle(
            ElevatorId id, String segment, JsonNode body, String accept, Principal principal) {
        if (!principal.hasScope("elevator:recall")) {
            return responses.problem(
                    HttpStatus.FORBIDDEN,
                    accept,
                    ElevatorRepresentations.forbidden(
                            "This operation requires the emergency recall key."));
        }

        try {
            handler.handle(new TriggerEmergencyRecallCommand(id));
        } catch (TriggerEmergencyRecallHandler.UnknownElevator unknown) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        } catch (CommandRefused refused) {
            return responses.problem(
                    HttpStatus.CONFLICT, accept, ElevatorRepresentations.conflict(segment, refused));
        }

        ElevatorView view = projection.find(id).orElseThrow();
        updates.publish(id, eventRenderer.render(ElevatorRepresentations.eventRepresentation(view)));
        return responses.ok(
                accept, ElevatorRepresentations.representation(segment, view, affordanceCatalog, principal));
    }
}
