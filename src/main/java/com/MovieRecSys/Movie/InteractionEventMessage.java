package com.MovieRecSys.Movie;

import java.io.Serializable;
import java.time.Instant;

/**
 * Lightweight message payload for interaction events published to RabbitMQ.
 * Contains only the data needed for profile projection — not the full entity.
 */
public record InteractionEventMessage(
        String eventId,
        String userId,
        String imdbId,
        String type,
        Integer ratingValue,
        Instant occurredAt
) implements Serializable {
    public static InteractionEventMessage from(InteractionEvent event) {
        return new InteractionEventMessage(
                event.getId().toHexString(),
                event.getUserId().toHexString(),
                event.getImdbId(),
                event.getType().name(),
                event.getRatingValue(),
                event.getOccurredAt()
        );
    }
}
