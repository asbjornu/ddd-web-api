package no.javazone.elevator.repository;

import java.util.List;
import no.javazone.elevator.model.Call;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CallRepository extends JpaRepository<Call, Long> {

    List<Call> findByElevatorIdOrderByCreatedAtAsc(Long elevatorId);
}
