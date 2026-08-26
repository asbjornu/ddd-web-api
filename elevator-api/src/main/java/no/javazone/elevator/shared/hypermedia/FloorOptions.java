package no.javazone.elevator.shared.hypermedia;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The building's own floor range (1..floors, see {@code
 * no.javazone.elevator.config.ElevatorProperties}), as an
 * {@link Field}'s {@code options} list -- so a client discovers how
 * many floors there are from whichever operation carries a {@code
 * floor} field, rather than a constant of its own. See {@code
 * AGENTS.md}'s "the client... may not hard-code a domain constant
 * (elevator id, floor count, travel timing)".
 */
public final class FloorOptions {

    private FloorOptions() {
    }

    public static List<String> upTo(int floors) {
        return IntStream.rangeClosed(1, floors).mapToObj(String::valueOf).toList();
    }
}
