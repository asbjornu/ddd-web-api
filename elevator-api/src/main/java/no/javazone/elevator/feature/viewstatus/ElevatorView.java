package no.javazone.elevator.feature.viewstatus;

/**
 * The read model itself: a plain projection, not the JPA entity --
 * {@link ElevatorViewProjection} maps between the two so nothing outside
 * this package ever sees {@link ElevatorViewEntity}.
 *
 * <p>Public, unlike the entity and repository either side of it: this
 * is the shape other feature slices depend on when they need the
 * current read model -- {@code feature.streamevents} for the SSE
 * payload, {@code feature.callelevator} for the representation a
 * command returns.
 */
public record ElevatorView(
        long id,
        int currentFloor,
        String state,
        String direction,
        String doorPosition,
        boolean obstructed,
        int weightKg,
        int capacityKg) {

    static ElevatorView from(ElevatorViewEntity entity) {
        return new ElevatorView(
                entity.getId(),
                entity.getCurrentFloor(),
                entity.getState(),
                entity.getDirection(),
                entity.getDoorPosition(),
                entity.isObstructed(),
                entity.getWeightKg(),
                entity.getCapacityKg());
    }
}
