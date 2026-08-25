package no.javazone.elevator.feature.triggeremergencyrecall;

import no.javazone.elevator.shared.domain.ElevatorId;

public record TriggerEmergencyRecallCommand(ElevatorId elevatorId) {
}
