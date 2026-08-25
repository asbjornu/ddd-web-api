package no.javazone.elevator.feature.entermaintenance;

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
 * Answers {@code "type": "EnterMaintenance"} on {@code POST /elevators/{id}}.
 *
 * <p>The scope check happens here, in the same place and the same way
 * a domain refusal would -- not in a security filter in front of it.
 * Missing {@code elevator:maintenance} is a bare 403 with no invented
 * problem type ({@link ElevatorRepresentations#forbidden}): unlike a
 * {@link CommandRefused} 409, the caller never had this command legally
 * available at all, which is also why {@code
 * EnterMaintenanceAffordanceContributor} omits the affordance for the
 * same caller in the first place -- see {@code docs/architecture.md}'s
 * "Key-switch and authorization" section.
 */
@Component
public class EnterMaintenanceController implements CommandEndpoint {

    private final EnterMaintenanceHandler handler;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final ElevatorStateJsonRenderer eventRenderer;
    private final AffordanceCatalog affordanceCatalog;
    private final RepresentationResponses responses;

    public EnterMaintenanceController(
            EnterMaintenanceHandler handler,
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
        return "EnterMaintenance";
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
            handler.handle(new EnterMaintenanceCommand(id));
        } catch (EnterMaintenanceHandler.UnknownElevator unknown) {
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
