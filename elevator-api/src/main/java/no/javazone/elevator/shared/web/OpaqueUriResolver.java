package no.javazone.elevator.shared.web;

import java.math.BigInteger;
import no.javazone.elevator.shared.domain.ElevatorId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The opaque style: the resource identifier is not the surrogate key,
 * and does not look like one. A reversible multiplicative scramble
 * (Knuth's multiplicative hash constant, modulo 2^32) rather than
 * anything cryptographic -- obscurity is not this class's job, only
 * proving that nothing downstream depends on the identifier being
 * sequential or guessable. See {@code docs/plan.html} &sect;8's "The
 * test that settles it": the same test suite must pass against both
 * this resolver and {@link ReadableUriResolver}.
 */
@Component
@ConditionalOnProperty(prefix = "elevator.uri", name = "style", havingValue = "opaque")
public class OpaqueUriResolver implements UriResolver {

    private static final BigInteger MODULUS = BigInteger.ONE.shiftLeft(32);
    private static final BigInteger MULTIPLIER = BigInteger.valueOf(2654435761L);
    private static final BigInteger INVERSE = MULTIPLIER.modInverse(MODULUS);
    private static final int RADIX = 36;

    @Override
    public String segmentFor(ElevatorId id) {
        BigInteger scrambled = BigInteger.valueOf(id.value())
                .multiply(MULTIPLIER)
                .mod(MODULUS);
        return scrambled.toString(RADIX);
    }

    @Override
    public ElevatorId resolve(String segment) {
        BigInteger scrambled = new BigInteger(segment, RADIX);
        long original = scrambled.multiply(INVERSE).mod(MODULUS).longValueExact();
        return new ElevatorId(original);
    }
}
