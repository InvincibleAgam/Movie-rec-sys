package com.MovieRecSys.Movie;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CollaborativeSignalRepository extends MongoRepository<CollaborativeSignal, String> {
    Optional<CollaborativeSignal> findByImdbId(String imdbId);

    List<CollaborativeSignal> findByImdbIdIn(List<String> imdbIds);
}
