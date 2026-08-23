package no.javazone.elevator.shared.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One pending landing call, belonging to the write side -- see
 * {@code no.javazone.elevator.shared.domain.LandingCall} for the domain
 * value object this maps to and from.
 */
@Entity
@Table(name = "landing_call")
public class LandingCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elevatorId;

    private int floor;

    /** {@code Direction}'s name, e.g. {@code "UP"}. */
    private String direction;

    protected LandingCallEntity() {
        // JPA
    }

    public LandingCallEntity(Long elevatorId, int floor, String direction) {
        this.elevatorId = elevatorId;
        this.floor = floor;
        this.direction = direction;
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

    public String getDirection() {
        return direction;
    }
}
