package no.javazone.elevator.shared.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CarCallJpaRepository extends JpaRepository<CarCallEntity, Long> {

    List<CarCallEntity> findByElevatorId(Long elevatorId);

    @Modifying
    @Query("delete from CarCallEntity c where c.elevatorId = :elevatorId")
    void deleteByElevatorId(@Param("elevatorId") Long elevatorId);
}
