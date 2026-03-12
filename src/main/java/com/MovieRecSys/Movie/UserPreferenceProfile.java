package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_preference_profiles")
public class UserPreferenceProfile {
    @Id
    private String id;

    @Indexed(unique = true)
    private ObjectId userId;

    private List<String> anchorImdbIds;
    private Map<String, Double> genreWeights;
    private Map<String, Double> keywordWeights;
    private Map<String, Double> directorWeights;
    private Instant updatedAt;
    private long sourceEventCount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ObjectId getUserId() {
        return userId;
    }

    public void setUserId(ObjectId userId) {
        this.userId = userId;
    }

    public List<String> getAnchorImdbIds() {
        return anchorImdbIds;
    }

    public void setAnchorImdbIds(List<String> anchorImdbIds) {
        this.anchorImdbIds = anchorImdbIds;
    }

    public Map<String, Double> getGenreWeights() {
        return genreWeights;
    }

    public void setGenreWeights(Map<String, Double> genreWeights) {
        this.genreWeights = genreWeights;
    }

    public Map<String, Double> getKeywordWeights() {
        return keywordWeights;
    }

    public void setKeywordWeights(Map<String, Double> keywordWeights) {
        this.keywordWeights = keywordWeights;
    }

    public Map<String, Double> getDirectorWeights() {
        return directorWeights;
    }

    public void setDirectorWeights(Map<String, Double> directorWeights) {
        this.directorWeights = directorWeights;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getSourceEventCount() {
        return sourceEventCount;
    }

    public void setSourceEventCount(long sourceEventCount) {
        this.sourceEventCount = sourceEventCount;
    }
}
