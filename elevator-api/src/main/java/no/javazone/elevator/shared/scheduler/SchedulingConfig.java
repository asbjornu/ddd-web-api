package no.javazone.elevator.shared.scheduler;

import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

/**
 * One background thread pool for scheduled arrivals -- see
 * {@link MovementScheduler}. A single elevator, so a single-threaded
 * scheduler is enough; a building with more would size this per
 * elevator or per shaft, not globally.
 */
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler movementTaskScheduler() {
        return new ConcurrentTaskScheduler(Executors.newSingleThreadScheduledExecutor());
    }
}
