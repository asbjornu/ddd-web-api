package no.javazone.elevator.shared.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One pending car call, belonging to the write side -- see
 * {@code no.javazone.elevator.shared.domain.CarCall} for the domain
 * value object this maps to and from.
 */
@Entity
@Table(name = "car_call")
public class CarCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elevatorId;

    private int floor;

    protected CarCallEntity() {
        // JPA
    }

    public CarCallEntity(Long elevatorId, int floor) {
        this.elevatorId = elevatorId;
        this.floor = floor;
    }

    public Long getId() {
        return id;
    }

    public Long getElevatorId() {
        return elevatorId;
    }

    public int getFloor() {
        return floor;
    }
}
