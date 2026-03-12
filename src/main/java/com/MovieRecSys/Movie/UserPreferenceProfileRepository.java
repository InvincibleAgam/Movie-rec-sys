package com.MovieRecSys.Movie;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserPreferenceProfileRepository extends MongoRepository<UserPreferenceProfile, String> {
    Optional<UserPreferenceProfile> findByUserId(ObjectId userId);
}
