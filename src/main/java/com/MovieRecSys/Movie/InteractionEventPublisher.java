package com.MovieRecSys.Movie;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Publishes interaction events to both MongoDB (source of truth) and
 * optionally to RabbitMQ (for async processing).
 *
 * When RabbitMQ is disabled, events are stored in MongoDB with PENDING status
 * and picked up by the scheduled projector (backward-compatible).
 *
 * When RabbitMQ is enabled, events are published to the message queue for
 * real-time processing by the idempotent consumer.
 */
@Service
public class InteractionEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(InteractionEventPublisher.class);

    private final InteractionEventRepository interactionEventRepository;
    private final RecommendationCacheService recommendationCacheService;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final boolean rabbitMQEnabled;
    private final Counter publishedCounter;

    public InteractionEventPublisher(
            InteractionEventRepository interactionEventRepository,
            RecommendationCacheService recommendationCacheService,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            MeterRegistry meterRegistry,
            @Value("${app.messaging.rabbitmq.enabled:false}") boolean rabbitMQEnabled
    ) {
        this.interactionEventRepository = interactionEventRepository;
        this.recommendationCacheService = recommendationCacheService;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.rabbitMQEnabled = rabbitMQEnabled;
        this.publishedCounter = Counter.builder("events.published")
                .description("Total interaction events published")
                .register(meterRegistry);
    }

    public void publishWatchlistAdded(ObjectId userId, String imdbId) {
        publish(userId, imdbId, InteractionEventType.WATCHLIST_ADDED, null);
    }

    public void publishWatchlistRemoved(ObjectId userId, String imdbId) {
        publish(userId, imdbId, InteractionEventType.WATCHLIST_REMOVED, null);
    }

    public void publishRatingSet(ObjectId userId, String imdbId, Integer ratingValue) {
        publish(userId, imdbId, InteractionEventType.RATING_SET, ratingValue);
    }

    public void publishReviewCreated(ObjectId userId, String imdbId) {
        publish(userId, imdbId, InteractionEventType.REVIEW_CREATED, null);
    }

    private void publish(ObjectId userId, String imdbId, InteractionEventType type, Integer ratingValue) {
        // Step 1: Always persist to MongoDB as source of truth
        InteractionEvent event = new InteractionEvent();
        event.setUserId(userId);
        event.setImdbId(imdbId);
        event.setType(type);
        event.setRatingValue(ratingValue);
        event.setStatus(InteractionEventStatus.PENDING);
        event.setOccurredAt(Instant.now());
        InteractionEvent savedEvent = interactionEventRepository.save(event);

        // Step 2: Invalidate caches immediately
        recommendationCacheService.invalidateUserCaches(userId);

        // Step 3: Optionally publish to RabbitMQ
        if (rabbitMQEnabled) {
            try {
                RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getObject();
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE,
                        RabbitMQConfig.ROUTING_KEY,
                        InteractionEventMessage.from(savedEvent)
                );
                log.debug("Published event {} to RabbitMQ", savedEvent.getId());
            } catch (Exception e) {
                log.warn("Failed to publish event {} to RabbitMQ, will be processed by scheduled projector",
                        savedEvent.getId(), e);
                // Graceful degradation: event is still in DB with PENDING status
            }
        }

        publishedCounter.increment();
    }
}
