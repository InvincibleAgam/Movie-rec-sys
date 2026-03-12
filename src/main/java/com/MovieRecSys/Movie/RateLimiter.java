package com.MovieRecSys.Movie;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-memory rate limiter using Bucket4j token-bucket algorithm.
 * Applies per-IP rate limiting to protect auth and review endpoints.
 *
 * Limits:
 *   - Auth endpoints: 10 requests per minute
 *   - Review endpoints: 5 reviews per minute
 *   - General API: 60 requests per minute
 */
@Component
public class RateLimiter {
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> reviewBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    public void checkAuthLimit(String clientId) {
        Bucket bucket = authBuckets.computeIfAbsent(clientId, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.simple(10, Duration.ofMinutes(1)))
                        .build());
        consumeOrThrow(bucket, "auth");
    }

    public void checkReviewLimit(String clientId) {
        Bucket bucket = reviewBuckets.computeIfAbsent(clientId, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.simple(5, Duration.ofMinutes(1)))
                        .build());
        consumeOrThrow(bucket, "review");
    }

    public void checkGeneralLimit(String clientId) {
        Bucket bucket = generalBuckets.computeIfAbsent(clientId, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.simple(60, Duration.ofMinutes(1)))
                        .build());
        consumeOrThrow(bucket, "general");
    }

    private void consumeOrThrow(Bucket bucket, String context) {
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded for " + context + " requests. Please try again later.");
        }
    }
}
