package no.javazone.elevator.repository;

import java.util.List;
import no.javazone.elevator.model.CarCall;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarCallRepository extends JpaRepository<CarCall, Long> {

    List<CarCall> findByElevatorIdOrderByCreatedAtAsc(Long elevatorId);

    List<CarCall> findByElevatorIdAndServedAtIsNullOrderByCreatedAtAsc(Long elevatorId);

    List<CarCall> findByElevatorIdAndServedAtIsNullAndFloor(Long elevatorId, int floor);
}
