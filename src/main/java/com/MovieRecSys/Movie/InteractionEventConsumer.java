package com.MovieRecSys.Movie;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Idempotent consumer for interaction events from RabbitMQ.
 *
 * Idempotency guarantee: Before processing, checks if the event has
 * already been marked as PROCESSED in the database. This ensures that
 * message retries and replays do not corrupt user profiles.
 */
@Component
@ConditionalOnProperty(name = "app.messaging.rabbitmq.enabled", havingValue = "true", matchIfMissing = false)
public class InteractionEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(InteractionEventConsumer.class);

    private final InteractionEventRepository interactionEventRepository;
    private final RecommendationProfileProjector profileProjector;
    private final RecommendationCacheService cacheService;
    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;

    public InteractionEventConsumer(
            InteractionEventRepository interactionEventRepository,
            RecommendationProfileProjector profileProjector,
            RecommendationCacheService cacheService,
            MeterRegistry meterRegistry
    ) {
        this.interactionEventRepository = interactionEventRepository;
        this.profileProjector = profileProjector;
        this.cacheService = cacheService;
        this.processedCounter = Counter.builder("events.processed")
                .description("Successfully processed interaction events")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("events.duplicates")
                .description("Duplicate interaction events skipped")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("events.failed")
                .description("Failed interaction events")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("events.processing.duration")
                .description("Time to process a single interaction event")
                .register(meterRegistry);
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void handleEvent(InteractionEventMessage message) {
        processingTimer.record(() -> {
            try {
                // Idempotency check
                InteractionEvent event = interactionEventRepository.findById(new ObjectId(message.eventId()))
                        .orElse(null);

                if (event == null) {
                    log.warn("Event not found in database: {}", message.eventId());
                    failedCounter.increment();
                    return;
                }

                if (event.getStatus() == InteractionEventStatus.PROCESSED) {
                    log.debug("Duplicate event detected, skipping: {}", message.eventId());
                    duplicateCounter.increment();
                    return;
                }

                // Process: rebuild the user's preference profile
                profileProjector.rebuildProfile(event.getUserId());

                // Mark as processed
                event.setStatus(InteractionEventStatus.PROCESSED);
                event.setProcessedAt(java.time.Instant.now());
                interactionEventRepository.save(event);

                // Invalidate caches
                cacheService.invalidateUserCaches(event.getUserId());

                processedCounter.increment();
                log.debug("Processed event {} for user {}", message.eventId(), message.userId());

            } catch (Exception e) {
                failedCounter.increment();
                log.error("Failed to process event {}: {}", message.eventId(), e.getMessage(), e);
                throw new RuntimeException("Event processing failed", e);
            }
        });
    }
}
