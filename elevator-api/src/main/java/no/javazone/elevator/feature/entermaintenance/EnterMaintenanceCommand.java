package no.javazone.elevator.feature.entermaintenance;

import no.javazone.elevator.shared.domain.ElevatorId;

public record EnterMaintenanceCommand(ElevatorId elevatorId) {
}
