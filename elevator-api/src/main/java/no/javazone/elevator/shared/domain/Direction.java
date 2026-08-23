package no.javazone.elevator.shared.domain;

/**
 * A landing call's direction. {@code NONE} covers the car's own default
 * when nothing is pending -- see {@link Doors} for the equally small
 * value object doors get.
 */
public enum Direction {
    UP,
    DOWN,
    NONE
}
