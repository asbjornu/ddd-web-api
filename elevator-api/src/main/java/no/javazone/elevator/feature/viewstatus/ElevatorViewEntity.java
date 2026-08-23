package no.javazone.elevator.feature.viewstatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The read side's own row, confined to this package -- see
 * {@code docs/architecture.md}'s "CQRS and domain events" section.
 * Never returned from a controller directly; {@link ViewStatusController}
 * maps it into a {@code Representation} at the boundary, the same rule
 * that keeps a JPA entity from leaking into a response elsewhere in this
 * refactor.
 *
 * <p>Today this table is seeded once (see the V8 migration) and never
 * written to again: no command has moved onto the new aggregate yet to
 * produce an event for a projection to fold in. Starting with slice 2,
 * {@link ElevatorViewProjection} becomes the only writer of this table,
 * driven by events rather than by a request.
 */
@Entity
@Table(name = "elevator_view")
public class ElevatorViewEntity {

    @Id
    private Long id;

    @Column(name = "current_floor")
    private int currentFloor;

    private String state;

    private String direction;

    @Column(name = "door_position")
    private String doorPosition;

    private boolean obstructed;

    @Column(name = "weight_kg")
    private int weightKg;

    @Column(name = "capacity_kg")
    private int capacityKg;

    protected ElevatorViewEntity() {
        // JPA
    }

    public Long getId() {
        return id;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public String getState() {
        return state;
    }

    public String getDirection() {
        return direction;
    }

    public String getDoorPosition() {
        return doorPosition;
    }

    public boolean isObstructed() {
        return obstructed;
    }

    public int getWeightKg() {
        return weightKg;
    }

    public int getCapacityKg() {
        return capacityKg;
    }
}
