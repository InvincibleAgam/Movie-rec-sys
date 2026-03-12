package com.MovieRecSys.Movie;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, ObjectId> {
    Optional<RefreshToken> findByTokenFamily(String tokenFamily);

    List<RefreshToken> findByUserId(ObjectId userId);

    List<RefreshToken> findByUserIdAndRevokedFalse(ObjectId userId);

    void deleteByUserId(ObjectId userId);
}
