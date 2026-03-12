package com.MovieRecSys.Movie;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InteractionEventRepository extends MongoRepository<InteractionEvent, ObjectId> {
    List<InteractionEvent> findByStatusOrderByOccurredAtAsc(InteractionEventStatus status);

    long countByUserId(ObjectId userId);
}
