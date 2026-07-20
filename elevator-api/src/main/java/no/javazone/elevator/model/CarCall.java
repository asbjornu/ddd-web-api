package no.javazone.elevator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A car call: a rider inside the car selecting a destination floor.
 * Reuses the same entity/DTO pattern as Call -- see the "Model reuse"
 * code smell in docs/architecture.md.
 */
@Entity
@Table(name = "car_calls")
@Getter
@Setter
@NoArgsConstructor
public class CarCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elevatorId;

    private int floor;

    private Instant createdAt;

    /** Null while the call is still pending. */
    private Instant servedAt;
}
