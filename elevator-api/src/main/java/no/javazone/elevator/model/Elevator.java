package no.javazone.elevator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The elevator aggregate root. This same class is used as the JPA
 * entity, the domain model, and (for now) the JSON representation
 * returned by the service API -- see the "Model reuse" code smell in
 * docs/architecture.md.
 */
@Entity
@Table(name = "elevators")
@Getter
@Setter
@NoArgsConstructor
public class Elevator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int currentFloor;

    @Enumerated(EnumType.STRING)
    private ElevatorState state;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    private DoorState doorState;

    private int weightCapacityKg;

    /**
     * The floor this elevator is currently travelling towards, if any
     * (only meaningful while state is movingUp/movingDown).
     */
    private Integer targetFloor;

    /**
     * When the elevator last transitioned into its current state. Real-time
     * behaviour (travel, door timing) is computed on read from this
     * timestamp rather than advanced by a background scheduler -- see
     * "Timing" in docs/architecture.md.
     */
    private Instant stateSince;
}
