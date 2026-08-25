package no.javazone.elevator.shared.web;

import tools.jackson.databind.JsonNode;
import no.javazone.elevator.shared.domain.ElevatorId;
import no.javazone.elevator.shared.security.Principal;
import org.springframework.http.ResponseEntity;

/**
 * Implemented by exactly one class per command slice: the endpoint half
 * of {@code feature.xxx}'s command, discovered under one shared URL by
 * {@link CommandsController} rather than owning a URL of its own.
 *
 * <p>{@code segment} is passed alongside the already-resolved
 * {@code id} because representations render the opaque URI segment the
 * caller used, not the identifier itself -- see {@code UriResolver}.
 *
 * <p>{@code principal} is always present, never {@code null} --
 * {@link Principal#ANONYMOUS} for an unauthenticated caller -- resolved
 * once by {@link CommandsController} per {@code docs/architecture.md}'s
 * "validate once at the border" reasoning. Most commands ignore it;
 * a privileged one (see {@code feature.entermaintenance}) checks it the
 * same way its {@code AffordanceContributor} does.
 */
public interface CommandEndpoint {

    /** The exact value this endpoint answers to in the request body's
     * {@code "type"} member -- conventionally the command record's own
     * simple name (e.g. {@code "CallElevator"}), and the same string
     * every {@code AffordanceContributor} offering this command must
     * place in its hidden {@code type} field so a client never has to
     * know it. */
    String type();

    ResponseEntity<String> handle(
            ElevatorId id, String segment, JsonNode body, String accept, Principal principal);
}
