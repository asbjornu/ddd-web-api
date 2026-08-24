package no.javazone.elevator.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The write side's own snapshot row -- confined to this package, never
 * returned from a controller, and never the same class as the read
 * side's {@code ElevatorViewEntity}. See
 * {@code docs/architecture.md}'s "CQRS and domain events" section.
 *
 * <p>A snapshot, not an event log: {@link ElevatorAggregateStore}
 * overwrites this row on every save rather than appending events and
 * replaying them. That is a deliberate simplification of "the aggregate
 * is the only thing that may refuse, and events are its only output" --
 * true event sourcing (append-only, rebuild-by-replay) is future work,
 * not this slice's.
 */
@Entity
@Table(name = "elevator_aggregate")
public class ElevatorAggregateEntity {

    @Id
    private Long id;

    @Column(name = "current_floor")
    private int currentFloor;

    /** One of {@code ElevatorState}'s permitted subtypes, lower camelCase. */
    private String state;

    private boolean obstructed;

    @Column(name = "door_position")
    private String doorPosition;

    @Column(name = "weight_kg")
    private int weightKg;

    @Column(name = "capacity_kg")
    private int capacityKg;

    @Column(name = "destination_floor")
    private Integer destinationFloor;

    protected ElevatorAggregateEntity() {
        // JPA
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isObstructed() {
        return obstructed;
    }

    public void setObstructed(boolean obstructed) {
        this.obstructed = obstructed;
    }

    public String getDoorPosition() {
        return doorPosition;
    }

    public void setDoorPosition(String doorPosition) {
        this.doorPosition = doorPosition;
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

    public Integer getDestinationFloor() {
        return destinationFloor;
    }

    public void setDestinationFloor(Integer destinationFloor) {
        this.destinationFloor = destinationFloor;
    }
}
