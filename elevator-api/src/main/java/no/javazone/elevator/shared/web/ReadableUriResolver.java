package no.javazone.elevator.shared.web;

import no.javazone.elevator.shared.domain.ElevatorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The readable style: the resource identifier is the surrogate key's
 * decimal string. Good for operators, logs and demos -- see
 * {@code docs/plan.html} &sect;8's "What stays readable, and what stays
 * public" -- and, because {@link OpaqueUriResolver} exists and the test
 * suite runs against both, never load-bearing for correctness.
 */
@Component
@ConditionalOnProperty(
        prefix = "elevator.uri",
        name = "style",
        havingValue = "readable",
        matchIfMissing = true)
public class ReadableUriResolver implements UriResolver {

    @Override
    public String segmentFor(ElevatorId id) {
        return Long.toString(id.value());
    }

    @Override
    public ElevatorId resolve(String segment) {
        return new ElevatorId(Long.parseLong(segment));
    }
}
