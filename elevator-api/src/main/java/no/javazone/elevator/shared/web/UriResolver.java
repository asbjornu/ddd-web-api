package no.javazone.elevator.shared.web;

import no.javazone.elevator.shared.domain.ElevatorId;

/**
 * Maps an {@link ElevatorId} to a resource identifier (a URI path
 * segment) and back -- the only place in the system allowed to know
 * both. Clients follow a URI the server issued; they never construct
 * one, and the surrogate key behind it never reaches the wire -- see
 * {@code docs/architecture.md}'s "Identifiers and URIs" section.
 *
 * <p>Two implementations exist, selected by the
 * {@code elevator.uri.style} property, precisely so the test suite can
 * run against both: the whole point is that nothing outside this
 * package may behave differently depending on which one is active.
 */
public interface UriResolver {

    /** The path segment identifying {@code id}, e.g. for {@code /elevators/{segment}}. */
    String segmentFor(ElevatorId id);

    /** The inverse of {@link #segmentFor(ElevatorId)}. */
    ElevatorId resolve(String segment);
}
