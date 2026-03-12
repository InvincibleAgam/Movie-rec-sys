package com.MovieRecSys.Movie;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RatingRepository extends MongoRepository<Rating, ObjectId> {
    Optional<Rating> findByUserIdAndImdbId(ObjectId userId, String imdbId);

    List<Rating> findByImdbId(String imdbId);

    List<Rating> findByUserId(ObjectId userId);
}
