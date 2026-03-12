package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Stores hashed refresh tokens for JWT auth.
 * Each user can have multiple active refresh tokens (one per device/session).
 */
@Document(collection = "refresh_tokens")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {
    @Id
    private ObjectId id;

    @Indexed
    private ObjectId userId;

    /**
     * BCrypt hash of the refresh token.
     * The raw token is only sent to the client once on creation.
     */
    private String tokenHash;

    @Indexed
    private String tokenFamily;

    private Instant createdAt;
    private Instant expiresAt;
    private boolean revoked;

    private List<String> auditLog;
}
