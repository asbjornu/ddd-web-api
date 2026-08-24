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
 * <p>Seeded once (see the V8 migration); {@link ElevatorViewProjection}
 * is the only writer after that, syncing this row from the write-side
 * aggregate after a command or a scheduled arrival changes it.
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

    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = currentFloor;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getDoorPosition() {
        return doorPosition;
    }

    public void setDoorPosition(String doorPosition) {
        this.doorPosition = doorPosition;
    }

    public boolean isObstructed() {
        return obstructed;
    }

    public void setObstructed(boolean obstructed) {
        this.obstructed = obstructed;
    }

    public int getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(int weightKg) {
        this.weightKg = weightKg;
    }

    public int getCapacityKg() {
        return capacityKg;
    }

    public void setCapacityKg(int capacityKg) {
        this.capacityKg = capacityKg;
    }
}
