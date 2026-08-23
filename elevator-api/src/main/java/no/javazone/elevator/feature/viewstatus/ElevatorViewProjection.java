package no.javazone.elevator.feature.viewstatus;

import java.util.Optional;
import no.javazone.elevator.shared.domain.ElevatorId;
import org.springframework.stereotype.Component;

/**
 * The query side's only entry point into {@link ElevatorViewRepository}
 * -- queries never touch the write-side aggregate, per
 * {@code docs/architecture.md}'s "CQRS and domain events" section.
 *
 * <p>Not yet a projection in the event-sourced sense: nothing folds a
 * {@code DomainEvent} into this table today, because no command has
 * moved onto the new aggregate yet to emit one (the read side has
 * nothing to read except the seed row). From slice 2 onward, this class
 * grows an {@code @EventListener} per event type its view cares about,
 * and starts calling {@link ElevatorViewUpdates#publish} after each
 * write so subscribers to {@code GET /elevators/{id}/events} hear about
 * it -- the name and the seam are both already right, only the
 * subscriptions are still empty.
 */
@Component
public class ElevatorViewProjection {

    private final ElevatorViewRepository repository;

    public ElevatorViewProjection(ElevatorViewRepository repository) {
        this.repository = repository;
    }

    public Optional<ElevatorView> find(ElevatorId id) {
        return repository.findById(id.value()).map(ElevatorView::from);
    }
}
