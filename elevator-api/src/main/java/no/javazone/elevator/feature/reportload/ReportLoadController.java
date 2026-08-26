package no.javazone.elevator.feature.reportload;

import tools.jackson.databind.JsonNode;
import java.util.Optional;
import no.javazone.elevator.feature.streamevents.ElevatorViewUpdates;
import no.javazone.elevator.feature.viewstatus.ElevatorView;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.CommandRefused;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.config.ElevatorProperties;
import no.javazone.elevator.shared.hypermedia.AffordanceCatalog;
import no.javazone.elevator.shared.web.CommandEndpoint;
import no.javazone.elevator.shared.security.Principal;
import no.javazone.elevator.shared.web.ElevatorRepresentations;
import no.javazone.elevator.shared.web.RepresentationResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Answers {@code "type": "ReportLoad"} on {@code POST /elevators/{id}},
 * replacing the old {@code WeightController} entirely -- there is
 * nothing left of it once this mapping moves.
 */
@Component
public class ReportLoadController implements CommandEndpoint {

    private final ReportLoadHandler handler;
    private final ElevatorViewProjection projection;
    private final ElevatorViewUpdates updates;
    private final AffordanceCatalog affordanceCatalog;
    private final ElevatorProperties properties;
    private final RepresentationResponses responses;

    public ReportLoadController(
            ReportLoadHandler handler,
            ElevatorViewProjection projection,
            ElevatorViewUpdates updates,
            AffordanceCatalog affordanceCatalog,
            ElevatorProperties properties,
            RepresentationResponses responses) {
        this.handler = handler;
        this.projection = projection;
        this.updates = updates;
        this.affordanceCatalog = affordanceCatalog;
        this.properties = properties;
        this.responses = responses;
    }

    @Override
    public String type() {
        return "ReportLoad";
    }

    @Override
    public ResponseEntity<String> handle(
            ElevatorId id, String segment, JsonNode body, String accept, Principal principal) {
        Optional<Integer> weightKg = parseWeight(body);
        if (weightKg.isEmpty()) {
            return responses.problem(
                    HttpStatus.BAD_REQUEST,
                    accept,
                    ElevatorRepresentations.badRequest(
                            "A load report needs a non-negative integer \"weightKg\"."));
        }

        try {
            handler.handle(new ReportLoadCommand(id, weightKg.get()));
        } catch (ReportLoadHandler.UnknownElevator unknown) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        } catch (CommandRefused refused) {
            return responses.problem(
                    HttpStatus.CONFLICT, accept, ElevatorRepresentations.conflict(segment, refused));
        }

        ElevatorView view = projection.find(id).orElseThrow();
        updates.publish(id, view);
        return responses.ok(accept, ElevatorRepresentations.representation(segment, view, affordanceCatalog, principal, properties));
    }

    private Optional<Integer> parseWeight(JsonNode body) {
        if (body == null || !body.hasNonNull("weightKg") || !body.get("weightKg").canConvertToInt()) {
            return Optional.empty();
        }
        int weightKg = body.get("weightKg").asInt();
        return weightKg < 0 ? Optional.empty() : Optional.of(weightKg);
    }
}
