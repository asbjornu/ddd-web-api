package no.javazone.elevator.shared.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The elevator aggregate root: pure Java, no Spring, no JPA, no Lombok --
 * see {@code docs/architecture.md}'s "Vertical slices" section for why
 * this class (and the rest of {@code shared.domain}) is the one thing
 * every slice shares rather than owns.
 *
 * <p>Command methods arrive one at a time, each with the slice that
 * needs it. Each returns the events it produced; the aggregate is the
 * only thing that may refuse (by throwing {@link CommandRefused}), and
 * events are its only other output -- see {@code
 * docs/architecture.md}'s "CQRS and domain events" section.
 *
 * <p>{@link #dispatch()} and {@link #arrive} are not rider-facing: the
 * first is called by {@link #call} and {@link #selectFloor} themselves
 * when the car was idle and now has somewhere to go; the second is
 * called only by {@code shared.scheduler}, once, at the instant it
 * computed when dispatching -- see {@link MovementStarted}'s Javadoc.
 */
public final class Elevator {

    private final ElevatorId id;
    private final Load load;
    private final RequestQueue queue;
    private Floor currentFloor;
    private ElevatorState state;
    private Doors doors;

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
     * all. Dispatches the car if it was idle with nowhere else to go.
     */
    public List<DomainEvent> call(Floor floor, Direction direction) {
        requireInService();
        List<DomainEvent> events = new ArrayList<>();
        queue.addLanding(new LandingCall(floor, direction));
        events.add(new ElevatorCalled(id, floor, direction, Instant.now()));
        dispatch().ifPresent(events::add);
        return events;
    }

    /**
     * A rider inside the car selects {@code floor} as a destination.
     * Refused while out of service, mid-recall, or overloaded -- an
     * overloaded car must not depart. Dispatches the car if it was idle.
     */
    public List<DomainEvent> selectFloor(Floor floor) {
        requireInService();
        if (load.isOverloaded()) {
            throw new CommandRefused("The car is overloaded.");
        }
        List<DomainEvent> events = new ArrayList<>();
        queue.addCar(new CarCall(floor));
        events.add(new FloorSelected(id, floor, Instant.now()));
        dispatch().ifPresent(events::add);
        return events;
    }

    /**
     * Commits to travelling to the next pending floor, if the car is
     * idle and has somewhere to go -- direction-committed via
     * {@link RequestQueue#next}. Does nothing (and returns empty) if
     * already moving, or if nothing is pending.
     */
    private Optional<DomainEvent> dispatch() {
        if (!(state instanceof ElevatorState.Idle)) {
            return Optional.empty();
        }
        // Idle carries no direction of its own (see ElevatorState's
        // Javadoc) -- there is nothing to have committed to yet, so the
        // nearest pending floor wins. Direction-committed ordering
        // applies to the calls RequestQueue.next() is choosing among,
        // not to memory of a previous, already-finished trip.
        Optional<Floor> next = queue.next(currentFloor, Direction.NONE);
        if (next.isEmpty()) {
            return Optional.empty();
        }
        Floor destination = next.get();
        Direction direction = destination.level() > currentFloor.level()
                ? Direction.UP
                : Direction.DOWN;
        Instant departedAt = Instant.now();
        Floor from = currentFloor;
        this.state = direction == Direction.UP
                ? new ElevatorState.MovingUp(destination)
                : new ElevatorState.MovingDown(destination);
        return Optional.of(new MovementStarted(id, from, destination, direction, departedAt));
    }

    /**
     * The car has arrived at {@code floor}, per the scheduler's own
     * clock -- not a rider's command. Opens the doors (which starts the
     * auto-close timer via {@link DoorsOpened}, same as an explicit
     * {@code OpenDoors} would) and clears whatever calls were waiting at
     * this floor.
     */
    public List<DomainEvent> arrive(Floor floor) {
        this.currentFloor = floor;
        queue.clearAt(floor);
        List<DomainEvent> events = new ArrayList<>(openTheDoors());
        events.addFirst(new FloorReached(id, floor, Instant.now()));
        return events;
    }

    /**
     * Explicit rider action: open the doors. Refused while the car is
     * moving or out of service/mid-recall -- opening doors mid-transit
     * is not a thing a real car does.
     */
    public List<DomainEvent> openDoors() {
        requireInService();
        if (state instanceof ElevatorState.MovingUp || state instanceof ElevatorState.MovingDown) {
            throw new CommandRefused("The car is moving.");
        }
        return openTheDoors();
    }

    private List<DomainEvent> openTheDoors() {
        this.state = new ElevatorState.DoorsOpen();
        this.doors = new Doors(Doors.DoorPosition.OPEN, doors.obstructed());
        return new ArrayList<>(List.of(new DoorsOpened(id, Instant.now())));
    }

    /**
     * Explicit rider action: close the doors. Refused unless they are
     * open, or while obstructed, or while overloaded -- an overloaded
     * car must not depart, so its doors are held open instead. Starts
     * the {@link DoorsClosingStarted} timer that {@link #finishClosingIfStillClosing}
     * later completes, unless obstruction intervenes first.
     */
    public List<DomainEvent> closeDoors() {
        if (!(state instanceof ElevatorState.DoorsOpen)) {
            throw new CommandRefused("The doors are not open.");
        }
        if (doors.obstructed()) {
            throw new CommandRefused("Obstruction detected.");
        }
        if (load.isOverloaded()) {
            throw new CommandRefused("Overload detected.");
        }
        return startClosing();
    }

    private List<DomainEvent> startClosing() {
        this.state = new ElevatorState.DoorsClosing();
        this.doors = new Doors(Doors.DoorPosition.CLOSING, doors.obstructed());
        return List.of(new DoorsClosingStarted(id, Instant.now()));
    }

    /**
     * Called only by {@code shared.scheduler}'s door timer, once, at the
     * open-door timeout: closes the doors automatically, unless they
     * were already closed by an explicit command, or the car is
     * obstructed or overloaded (in which case nothing happens, and
     * nothing is rescheduled -- an honest simplification of the
     * old service's retry-forever behaviour; see this slice's commit
     * message).
     */
    public List<DomainEvent> autoCloseIfStillOpen() {
        if (!(state instanceof ElevatorState.DoorsOpen) || doors.obstructed() || load.isOverloaded()) {
            return List.of();
        }
        return startClosing();
    }

    /**
     * Called only by {@code shared.scheduler}'s door timer, once, at the
     * close-door duration: finishes closing, unless an obstruction
     * already re-opened the doors in the meantime. Dispatches the next
     * pending call, if the queue has one -- this is what lets a second
     * queued call actually get served, once the first stop's doors are
     * done with.
     */
    public List<DomainEvent> finishClosingIfStillClosing() {
        if (!(state instanceof ElevatorState.DoorsClosing)) {
            return List.of();
        }
        this.state = new ElevatorState.Idle();
        this.doors = new Doors(Doors.DoorPosition.CLOSED, false);
        List<DomainEvent> events = new ArrayList<>();
        events.add(new DoorsClosed(id, Instant.now()));
        dispatch().ifPresent(events::add);
        return events;
    }

    /**
     * The simulated obstruction sensor: refused unless the doors are
     * currently closing (there is nothing to obstruct otherwise).
     * Immediately re-opens the doors, restarting the auto-close cycle
     * via {@link DoorsOpened} -- the doors will keep trying to close
     * and keep being blocked until {@link #clearObstruction} is called,
     * matching a real light curtain.
     */
    public List<DomainEvent> obstructDoors() {
        if (!(state instanceof ElevatorState.DoorsClosing)) {
            throw new CommandRefused("The doors are not closing.");
        }
        List<DomainEvent> events = new ArrayList<>();
        events.add(new DoorsObstructed(id, Instant.now()));
        this.doors = new Doors(Doors.DoorPosition.OPEN, true);
        this.state = new ElevatorState.DoorsOpen();
        events.add(new DoorsOpened(id, Instant.now()));
        return events;
    }

    /**
     * Clears a simulated obstruction. Refused unless one is present.
     * Does not itself move the doors -- whatever state they are
     * currently in (almost always open, held there by the obstruction)
     * stands until the next close attempt, explicit or automatic.
     */
    public List<DomainEvent> clearObstruction() {
        if (!doors.obstructed()) {
            throw new CommandRefused("The doors are not obstructed.");
        }
        this.doors = new Doors(doors.position(), false);
        return List.of(new ObstructionCleared(id, Instant.now()));
    }

    private void requireInService() {
        if (state instanceof ElevatorState.OutOfService) {
            throw new CommandRefused("The elevator is out of service.");
        }
        if (state instanceof ElevatorState.EmergencyRecall) {
            throw new CommandRefused("The elevator is undergoing an emergency recall.");
        }
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
