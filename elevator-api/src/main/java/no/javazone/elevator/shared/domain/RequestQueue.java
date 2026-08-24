package no.javazone.elevator.shared.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pending landing calls and car calls, served in direction-committed
 * order -- a simplified SCAN/LOOK algorithm. This class is where
 * "Feature Envy" and "Long Method" would otherwise live if
 * request-queue logic leaked into {@code Elevator} or a service class
 * -- see {@code docs/architecture.md}'s "Core workflows, as commands"
 * section.
 *
 * <p>Simplified relative to the CRUD service it replaces: once the car
 * commits to a destination via {@link #next}, a later call does not
 * retarget it mid-journey (no "extra stop on the way") -- only ordering
 * across separately-dispatched trips is direction-committed. Restoring
 * mid-journey interruption is future work, not this slice's; see
 * {@code docs/architecture.md}'s slice 3 roadmap entry.
 */
public final class RequestQueue {

    private final List<LandingCall> landingCalls;
    private final List<CarCall> carCalls;

    private RequestQueue(List<LandingCall> landingCalls, List<CarCall> carCalls) {
        this.landingCalls = new ArrayList<>(landingCalls);
        this.carCalls = new ArrayList<>(carCalls);
    }

    public static RequestQueue empty() {
        return new RequestQueue(List.of(), List.of());
    }

    public static RequestQueue of(List<LandingCall> landingCalls, List<CarCall> carCalls) {
        return new RequestQueue(landingCalls, carCalls);
    }

    /**
     * Adds a landing call, unless one for the same floor and direction
     * is already pending -- two riders pressing the same landing button
     * is one call, not two. Returns whether it was actually added.
     */
    public boolean addLanding(LandingCall call) {
        if (landingCalls.contains(call)) {
            return false;
        }
        landingCalls.add(call);
        return true;
    }

    /** Adds a car call, unless one for the same floor is already pending. */
    public boolean addCar(CarCall call) {
        if (carCalls.contains(call)) {
            return false;
        }
        carCalls.add(call);
        return true;
    }

    public List<LandingCall> pendingLandingCalls() {
        return List.copyOf(landingCalls);
    }

    public List<CarCall> pendingCarCalls() {
        return List.copyOf(carCalls);
    }

    /**
     * The next floor to serve from {@code current}, travelling
     * {@code direction} if already committed to one -- a simplified
     * SCAN/LOOK: continue in the same direction while a pending floor
     * remains ahead, reverse only once nothing remains, and pick the
     * nearest pending floor when idle (no direction yet).
     */
    public Optional<Floor> next(Floor current, Direction direction) {
        Set<Integer> pending = pendingFloors();
        pending.remove(current.level());
        if (pending.isEmpty()) {
            return Optional.empty();
        }

        int currentLevel = current.level();
        return switch (direction) {
            case UP -> pending.stream()
                    .filter(level -> level > currentLevel)
                    .min(Comparator.naturalOrder())
                    .or(() -> pending.stream().filter(level -> level < currentLevel)
                            .max(Comparator.naturalOrder()))
                    .map(Floor::new);
            case DOWN -> pending.stream()
                    .filter(level -> level < currentLevel)
                    .max(Comparator.naturalOrder())
                    .or(() -> pending.stream().filter(level -> level > currentLevel)
                            .min(Comparator.naturalOrder()))
                    .map(Floor::new);
            case NONE -> pending.stream()
                    .min(Comparator.comparingInt(level -> Math.abs(level - currentLevel)))
                    .map(Floor::new);
        };
    }

    /** Removes any pending landing or car call at {@code floor} -- it has now been served. */
    public void clearAt(Floor floor) {
        landingCalls.removeIf(call -> call.floor().equals(floor));
        carCalls.removeIf(call -> call.floor().equals(floor));
    }

    /** Discards every pending call -- entering maintenance or an emergency recall. */
    public void clear() {
        landingCalls.clear();
        carCalls.clear();
    }

    private Set<Integer> pendingFloors() {
        return Stream.concat(
                        landingCalls.stream().map(call -> call.floor().level()),
                        carCalls.stream().map(call -> call.floor().level()))
                .collect(java.util.stream.Collectors.toCollection(java.util.HashSet::new));
    }
}
