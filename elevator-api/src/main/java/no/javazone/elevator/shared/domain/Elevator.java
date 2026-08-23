package no.javazone.elevator.shared.domain;

/**
 * The elevator aggregate root: pure Java, no Spring, no JPA, no Lombok --
 * see {@code docs/architecture.md}'s "Vertical slices" section for why
 * this class (and the rest of {@code shared.domain}) is the one thing
 * every slice shares rather than owns.
 *
 * <p>This is the domain skeleton only. No command methods exist yet:
 * {@code call}, {@code selectFloor} and the rest arrive one at a time,
 * each with the slice that needs it, starting with {@code CallElevator}
 * in slice 2. Adding them ahead of that slice would be exactly the
 * Speculative Generality this refactor exists to remove elsewhere.
 */
public final class Elevator {

    private final ElevatorId id;
    private final Floor currentFloor;
    private final ElevatorState state;
    private final Doors doors;
    private final Load load;

    private Elevator(
            ElevatorId id,
            Floor currentFloor,
            ElevatorState state,
            Doors doors,
            Load load) {
        this.id = id;
        this.currentFloor = currentFloor;
        this.state = state;
        this.doors = doors;
        this.load = load;
    }

    /**
     * The elevator's initial state: idle, doors closed, at the given
     * floor, empty.
     */
    public static Elevator seed(ElevatorId id, Floor currentFloor, int capacityKilograms) {
        return new Elevator(
                id,
                currentFloor,
                new ElevatorState.Idle(),
                Doors.closed(),
                new Load(0, capacityKilograms));
    }

    public ElevatorId id() {
        return id;
    }

    public Floor currentFloor() {
        return currentFloor;
    }

    public ElevatorState state() {
        return state;
    }

    public Doors doors() {
        return doors;
    }

    public Load load() {
        return load;
    }
}
