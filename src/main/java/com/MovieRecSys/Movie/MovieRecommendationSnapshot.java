package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "movie_recommendation_snapshots")
public class MovieRecommendationSnapshot {
    @Id
    private String id;

    @Indexed(unique = true)
    private String imdbId;

    private List<String> candidateImdbIds;
    private Instant generatedAt;

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

    public List<String> getCandidateImdbIds() {
        return candidateImdbIds;
    }

    public void setCandidateImdbIds(List<String> candidateImdbIds) {
        this.candidateImdbIds = candidateImdbIds;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
