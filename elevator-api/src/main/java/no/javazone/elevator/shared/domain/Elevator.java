package no.javazone.elevator.shared.domain;

import java.time.Instant;
import java.util.List;

/**
 * The elevator aggregate root: pure Java, no Spring, no JPA, no Lombok --
 * see {@code docs/architecture.md}'s "Vertical slices" section for why
 * this class (and the rest of {@code shared.domain}) is the one thing
 * every slice shares rather than owns.
 *
 * <p>Command methods arrive one at a time, each with the slice that
 * needs it -- {@link #call(Floor, Direction)} lands with slice 2;
 * {@code selectFloor} and the rest follow later. Adding them ahead of
 * their slice would be exactly the Speculative Generality this refactor
 * exists to remove elsewhere. Each returns the events it produced; the
 * aggregate is the only thing that may refuse (by throwing
 * {@link CommandRefused}), and events are its only other output -- see
 * {@code docs/architecture.md}'s "CQRS and domain events" section.
 */
public final class Elevator {

    private final ElevatorId id;
    private final Floor currentFloor;
    private final ElevatorState state;
    private final Doors doors;
    private final Load load;
    private final RequestQueue queue;

    private Elevator(
            ElevatorId id,
            Floor currentFloor,
            ElevatorState state,
            Doors doors,
            Load load,
            RequestQueue queue) {
        this.id = id;
        this.currentFloor = currentFloor;
        this.state = state;
        this.doors = doors;
        this.load = load;
        this.queue = queue;
    }

    /**
     * The elevator's initial state: idle, doors closed, at the given
     * floor, empty, with nothing queued.
     */
    public static Elevator seed(ElevatorId id, Floor currentFloor, int capacityKilograms) {
        return new Elevator(
                id,
                currentFloor,
                new ElevatorState.Idle(),
                Doors.closed(),
                new Load(0, capacityKilograms),
                RequestQueue.empty());
    }

    /**
     * Reconstructs an elevator from persisted state -- the write side's
     * own adapter's job (see {@code shared.persistence}), never a
     * command's.
     */
    public static Elevator restore(
            ElevatorId id,
            Floor currentFloor,
            ElevatorState state,
            Doors doors,
            Load load,
            RequestQueue queue) {
        return new Elevator(id, currentFloor, state, doors, load, queue);
    }

    /**
     * A rider at {@code floor} requests the car, heading {@code
     * direction}. Refused while the car is out of service or mid-recall
     * -- riders cannot call an elevator that is not answering calls at
     * all. Does not otherwise change {@link #state()}; scheduling the
     * car to actually serve the call is slice 3's job.
     */
    public List<DomainEvent> call(Floor floor, Direction direction) {
        if (state instanceof ElevatorState.OutOfService) {
            throw new CommandRefused("The elevator is out of service.");
        }
        if (state instanceof ElevatorState.EmergencyRecall) {
            throw new CommandRefused("The elevator is undergoing an emergency recall.");
        }
        queue.addLanding(new LandingCall(floor, direction));
        return List.of(new ElevatorCalled(id, floor, direction, Instant.now()));
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

    public RequestQueue queue() {
        return queue;
    }
}
