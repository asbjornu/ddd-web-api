package no.javazone.elevator.shared.domain;

/**
 * The elevator aggregate's domain identity. This is a surrogate
 * identifier's domain-side counterpart: {@code ElevatorId} is what the
 * aggregate and its commands carry, and it is deliberately not a resource
 * identifier.
 *
 * <p>Mapping an {@code ElevatorId} to (and from) a URI is the web layer's
 * job alone -- see {@code shared.web.UriResolver} -- so that the
 * persistence primary key never has to be the same value as the one that
 * appears in a URL. Nothing in this class knows that mapping exists.
 */
public record ElevatorId(long value) {

    public ElevatorId {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "ElevatorId must be positive, got " + value);
        }
    }
}
