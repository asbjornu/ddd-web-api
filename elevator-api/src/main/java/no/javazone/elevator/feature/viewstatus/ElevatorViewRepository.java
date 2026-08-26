package no.javazone.elevator.feature.viewstatus;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Query-side persistence, confined to this slice.
 */
interface ElevatorViewRepository extends JpaRepository<ElevatorViewEntity, Long> {

    /** Every known elevator's id, for {@code GET /elevators} -- the one
     * place a client discovers which elevators exist rather than being
     * told one, since a building may have more than the single one
     * seeded today. */
    @Query("select e.id from ElevatorViewEntity e order by e.id")
    List<Long> findAllIds();
}
