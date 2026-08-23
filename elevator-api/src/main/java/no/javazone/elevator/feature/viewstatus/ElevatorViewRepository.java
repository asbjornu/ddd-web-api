package no.javazone.elevator.feature.viewstatus;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Query-side persistence, confined to this slice.
 */
interface ElevatorViewRepository extends JpaRepository<ElevatorViewEntity, Long> {
}
