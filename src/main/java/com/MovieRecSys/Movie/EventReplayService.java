package com.MovieRecSys.Movie;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Event Replay Service — the "standout technical spike."
 *
 * Replays all interaction events from the MongoDB event store back into
 * the RabbitMQ queue to fully rebuild all user preference profiles from
 * raw event history. This is critical for:
 *   - Recovering from corrupted materialized state
 *   - Migrating to new profile computation logic
 *   - Disaster recovery
 *
 * Safety:
 *   - Only one replay can run at a time (AtomicBoolean guard)
 *   - Events are reset to PENDING before replay
 *   - The idempotent consumer handles duplicates safely
 */
@Service
@ConditionalOnProperty(name = "app.messaging.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class EventReplayService {
    private static final Logger log = LoggerFactory.getLogger(EventReplayService.class);

    private final InteractionEventRepository interactionEventRepository;
    private final UserPreferenceProfileRepository userPreferenceProfileRepository;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final AtomicBoolean replayInProgress = new AtomicBoolean(false);
    private final AtomicLong lastReplayCount = new AtomicLong(0);
    private final AtomicLong lastReplayDurationMs = new AtomicLong(0);

    public EventReplayService(
            InteractionEventRepository interactionEventRepository,
            UserPreferenceProfileRepository userPreferenceProfileRepository,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider
    ) {
        this.interactionEventRepository = interactionEventRepository;
        this.userPreferenceProfileRepository = userPreferenceProfileRepository;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
    }

    /**
     * Trigger a full event replay.
     *
     * @return a report of the replay operation
     */
    public ReplayReport replay() {
        if (!replayInProgress.compareAndSet(false, true)) {
            return new ReplayReport(false, 0, 0, "A replay is already in progress");
        }

        try {
            log.info("Starting event replay");
            long startTime = System.currentTimeMillis();

            // Step 1: Clear all materialized profiles
            userPreferenceProfileRepository.deleteAll();
            log.info("Cleared all user preference profiles");

            // Step 2: Reset all events to PENDING
            List<InteractionEvent> allEvents = interactionEventRepository.findAll();
            allEvents.forEach(event -> {
                event.setStatus(InteractionEventStatus.PENDING);
                event.setProcessedAt(null);
            });
            interactionEventRepository.saveAll(allEvents);
            log.info("Reset {} events to PENDING", allEvents.size());

            // Step 3: Republish all events to the queue
            RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getObject();
            int published = 0;
            for (InteractionEvent event : allEvents) {
                InteractionEventMessage message = InteractionEventMessage.from(event);
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE,
                        RabbitMQConfig.ROUTING_KEY,
                        message
                );
                published++;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            lastReplayCount.set(published);
            lastReplayDurationMs.set(elapsed);
            log.info("Event replay complete: {} events published in {} ms", published, elapsed);

            return new ReplayReport(true, published, elapsed, "Replay completed successfully");
        } catch (Exception e) {
            log.error("Event replay failed: {}", e.getMessage(), e);
            return new ReplayReport(false, 0, 0, "Replay failed: " + e.getMessage());
        } finally {
            replayInProgress.set(false);
        }
    }

    public ReplayStatus status() {
        return new ReplayStatus(
                replayInProgress.get(),
                lastReplayCount.get(),
                lastReplayDurationMs.get()
        );
    }

    public record ReplayReport(boolean success, int eventsPublished, long durationMs, String message) {}
    public record ReplayStatus(boolean inProgress, long lastReplayCount, long lastReplayDurationMs) {}
}
