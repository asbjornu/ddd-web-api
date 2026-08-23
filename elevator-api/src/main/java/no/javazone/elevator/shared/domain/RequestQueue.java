package no.javazone.elevator.shared.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Pending landing calls (and, from slice 3, car calls), served in
 * direction-committed order -- a simplified SCAN/LOOK algorithm. This
 * class is where "Feature Envy" and "Long Method" would otherwise live
 * if request-queue logic leaked into {@code Elevator} or a service
 * class -- see {@code docs/architecture.md}'s "Core workflows, as
 * commands" section.
 *
 * <p>Only landing calls exist today. SCAN/LOOK ordering and car calls
 * arrive with slice 3 (Select floor); adding them now, before a command
 * needs them, would be Speculative Generality.
 */
public final class RequestQueue {

    private final List<LandingCall> landingCalls;

    private RequestQueue(List<LandingCall> landingCalls) {
        this.landingCalls = new ArrayList<>(landingCalls);
    }

    public static RequestQueue empty() {
        return new RequestQueue(List.of());
    }

    public static RequestQueue of(List<LandingCall> landingCalls) {
        return new RequestQueue(landingCalls);
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

    public List<LandingCall> pendingLandingCalls() {
        return List.copyOf(landingCalls);
    }
}
