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
 *
 * <p>Deliberately carries no identifier of its own. {@link
 * ElevatorViewProjection#find} is already keyed by {@code ElevatorId},
 * and every renderer builds its representation from an explicit,
 * hand-picked property list (see {@code ElevatorRepresentations}) that
 * was never going to include a surrogate key -- but a field named
 * {@code id} sitting unused on a record every renderer receives was a
 * loaded gun for whichever renderer gets added next and forgets to
 * leave it out. See {@code docs/plan.html} &sect;8's "Identifiers and
 * URIs": the surrogate key belongs to the persistence layer alone.
 */
public record ElevatorView(
        int currentFloor,
        String state,
        String direction,
        String doorPosition,
        boolean obstructed,
        int weightKg,
        int capacityKg,
        Integer destinationFloor) {

    static ElevatorView from(ElevatorViewEntity entity) {
        return new ElevatorView(
                entity.getCurrentFloor(),
                entity.getState(),
                entity.getDirection(),
                entity.getDoorPosition(),
                entity.isObstructed(),
                entity.getWeightKg(),
                entity.getCapacityKg(),
                entity.getDestinationFloor());
    }
}
