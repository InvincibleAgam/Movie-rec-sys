package com.MovieRecSys.Movie;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MovieRecommendationSnapshotRepository extends MongoRepository<MovieRecommendationSnapshot, String> {
    Optional<MovieRecommendationSnapshot> findByImdbId(String imdbId);
}
