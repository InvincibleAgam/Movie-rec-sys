package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Stores precomputed item-item collaborative similarity scores.
 * Each document maps a single movie to its co-occurrence neighbors
 * derived from users who rated or watchlisted both items.
 */
@Document(collection = "collaborative_signals")
public class CollaborativeSignal {
    @Id
    private String id;

    @Indexed(unique = true)
    private String imdbId;

    /**
     * Maps neighbor imdbId -> co-occurrence score.
     * Score = sum of (1 / log2(2 + userActivityCount)) across shared users,
     * weighted by rating overlap when both users rated both movies.
     */
    private Map<String, Double> neighborScores;

    private Instant computedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImdbId() {
        return imdbId;
    }

    public void setImdbId(String imdbId) {
        this.imdbId = imdbId;
    }

    public Map<String, Double> getNeighborScores() {
        return neighborScores;
    }

    public void setNeighborScores(Map<String, Double> neighborScores) {
        this.neighborScores = neighborScores;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
