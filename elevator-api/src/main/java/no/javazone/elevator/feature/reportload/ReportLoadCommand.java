package no.javazone.elevator.feature.reportload;

import no.javazone.elevator.shared.domain.ElevatorId;

/** Sensor telemetry: the simulated scale reports the car's current weight. */
public record ReportLoadCommand(ElevatorId elevatorId, int weightKg) {
}
