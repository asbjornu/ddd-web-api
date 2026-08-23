package no.javazone.elevator.shared.domain;

/**
 * The car's current load, owning the overload rule itself rather than
 * leaving callers to compare a bare kilogram figure against a
 * capacity they have to already know -- see the "Primitive Obsession" /
 * "Data Clumps" smells this replaces.
 */
public record Load(int kilograms, int capacityKilograms) {

    public Load {
        if (kilograms < 0) {
            throw new IllegalArgumentException(
                    "Load cannot be negative, got " + kilograms);
        }
        if (capacityKilograms <= 0) {
            throw new IllegalArgumentException(
                    "Capacity must be positive, got " + capacityKilograms);
        }
    }

    public boolean isOverloaded() {
        return kilograms > capacityKilograms;
    }
}
