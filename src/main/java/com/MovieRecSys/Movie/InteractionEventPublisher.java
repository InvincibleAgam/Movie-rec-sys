package com.MovieRecSys.Movie;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
public class InteractionEventPublisher {
    private final InteractionEventRepository interactionEventRepository;

    public InteractionEventPublisher(InteractionEventRepository interactionEventRepository) {
        this.interactionEventRepository = interactionEventRepository;
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
        InteractionEvent event = new InteractionEvent();
        event.setUserId(userId);
        event.setImdbId(imdbId);
        event.setType(type);
        event.setRatingValue(ratingValue);
        event.setStatus(InteractionEventStatus.PENDING);
        event.setOccurredAt(Instant.now());
        interactionEventRepository.save(event);
    }
}
