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
 * A landing call: a rider at a floor requesting the car. This same
 * class is used as the JPA entity, domain model, and JSON
 * representation -- see the "Model reuse" code smell in
 * docs/architecture.md.
 */
@Entity
@Table(name = "calls")
@Getter
@Setter
@NoArgsConstructor
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long elevatorId;

    private int floor;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private Instant createdAt;

    /** Null while the call is still pending. */
    private Instant servedAt;
}
