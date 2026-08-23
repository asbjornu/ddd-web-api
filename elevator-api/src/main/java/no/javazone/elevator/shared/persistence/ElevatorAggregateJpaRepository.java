package no.javazone.elevator.shared.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ElevatorAggregateJpaRepository extends JpaRepository<ElevatorAggregateEntity, Long> {
}
