package com.MovieRecSys.Movie;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AppUserRepository extends MongoRepository<AppUser, ObjectId> {
    Optional<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByAuthToken(String authToken);
}
