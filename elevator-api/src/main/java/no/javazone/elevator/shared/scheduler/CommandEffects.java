package no.javazone.elevator.shared.scheduler;

import java.util.List;
import no.javazone.elevator.feature.viewstatus.ElevatorViewProjection;
import no.javazone.elevator.shared.domain.DomainEvent;
import no.javazone.elevator.shared.domain.Elevator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * What every command handler (and every scheduler, for its own later
 * scheduled transition) does after a successful
 * {@code aggregate.command(...)} call: fold the result into the read
 * model, and publish each event for whichever scheduler cares to react
 * -- {@link MovementScheduler} to {@code MovementStarted},
 * {@link DoorScheduler} to {@code DoorsOpened}/{@code DoorsClosingStarted}.
 * Publishing rather than calling the schedulers directly is what keeps
 * this class, and the schedulers, from depending on each other: neither
 * scheduler needs to know the other exists, even though one's scheduled
 * transition can produce an event the other reacts to (closing the
 * doors can dispatch the next call, opening them can start a new
 * auto-close timer).
 */
@Component
public class CommandEffects {

    private final ElevatorViewProjection projection;
    private final ApplicationEventPublisher publisher;

    public CommandEffects(ElevatorViewProjection projection, ApplicationEventPublisher publisher) {
        this.projection = projection;
        this.publisher = publisher;
    }

    public void apply(Elevator elevator, List<DomainEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        projection.syncFrom(elevator);
        events.forEach(publisher::publishEvent);
    }
}
