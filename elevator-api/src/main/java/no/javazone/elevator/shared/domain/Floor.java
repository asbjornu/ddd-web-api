package no.javazone.elevator.shared.domain;

/**
 * A floor in the building, as a domain identifier rather than a bare
 * {@code int} -- see the "Primitive Obsession" smell this replaces.
 *
 * <p>Floor numbers are part of the ubiquitous language (riders genuinely
 * say "floor 3") and are legitimately public field values, unlike the
 * elevator's own surrogate id -- see {@code docs/architecture.md}'s
 * "Identifiers and URIs" section for the distinction.
 */
public record Floor(int level, boolean recallFloor) {

    public Floor {
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Floor level must be at least 1, got " + level);
        }
    }

    /**
     * Constructs a non-recall floor. Most floors are not the recall
     * floor; use the two-argument constructor for the one that is.
     */
    public Floor(int level) {
        this(level, false);
    }

    public boolean isRecallFloor() {
        return recallFloor;
    }
}
