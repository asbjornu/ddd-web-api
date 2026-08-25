package no.javazone.elevator.feature.exitmaintenance;

import no.javazone.elevator.shared.domain.ElevatorId;

public record ExitMaintenanceCommand(ElevatorId elevatorId) {
}
