package no.javazone.elevator.shared.web;

import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.security.Principal;
import no.javazone.elevator.shared.security.PrincipalResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /elevators/{id}}: the one URL every command in this API
 * is invoked through -- the same URL {@code GET} already reads the
 * elevator's representation from; a POST that changes it and a GET
 * that reads it are two methods on one resource, not two resources.
 * Which behaviour a POST invokes is the request body's job, not the
 * request line's: the body's {@code "type"} member names the command
 * (e.g. {@code "CallElevator"}), exactly the string an
 * {@link no.javazone.elevator.shared.hypermedia.Affordance}'s hidden
 * {@code type} field already carries back from whichever
 * representation offered it. This controller only resolves the
 * elevator and dispatches by that name; each slice's own
 * {@link no.javazone.elevator.shared.hypermedia.AffordanceContributor}
 * still decides, independently, whether its command is legal right now.
 */
@RestController
public class CommandsController {

    private final UriResolver uriResolver;
    private final Map<String, CommandEndpoint> endpointsByType;
    private final RepresentationResponses responses;
    private final PrincipalResolver principalResolver;

    public CommandsController(
            UriResolver uriResolver,
            List<CommandEndpoint> endpoints,
            RepresentationResponses responses,
            PrincipalResolver principalResolver) {
        this.uriResolver = uriResolver;
        this.endpointsByType = endpoints.stream()
                .collect(Collectors.toMap(CommandEndpoint::type, Function.identity()));
        this.responses = responses;
        this.principalResolver = principalResolver;
    }

    @PostMapping("/elevators/{segment}")
    public ResponseEntity<String> dispatch(
            @PathVariable String segment,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        Optional<ElevatorId> id = resolve(segment);
        if (id.isEmpty()) {
            return responses.problem(
                    HttpStatus.NOT_FOUND, accept, ElevatorRepresentations.notFound(segment));
        }

        String type = commandType(body);
        CommandEndpoint endpoint = type == null ? null : endpointsByType.get(type);
        if (endpoint == null) {
            return responses.problem(
                    HttpStatus.BAD_REQUEST,
                    accept,
                    ElevatorRepresentations.badRequest(
                            "The request body must include a \"type\" naming a known command."));
        }

        return endpoint.handle(id.get(), segment, body, accept, principalResolver.resolve());
    }

    private String commandType(JsonNode body) {
        if (body == null || !body.hasNonNull("type")) {
            return null;
        }
        return body.get("type").asText(null);
    }

    private Optional<ElevatorId> resolve(String segment) {
        try {
            return Optional.of(uriResolver.resolve(segment));
        } catch (RuntimeException invalidSegment) {
            return Optional.empty();
        }
    }
}
