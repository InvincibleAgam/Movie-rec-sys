package com.MovieRecSys.Movie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Generates candidates from collaborative filtering co-occurrence data.
 * This is the key signal that breaks the content-only bubble.
 */
@Component
public class CollaborativeCandidateGenerator implements CandidateGenerator {
    private final CollaborativeFilteringService collaborativeFilteringService;

    public CollaborativeCandidateGenerator(CollaborativeFilteringService collaborativeFilteringService) {
        this.collaborativeFilteringService = collaborativeFilteringService;
    }

    @Override
    public String name() {
        return "collaborative";
    }

    @Override
    public List<String> generateForMovie(String sourceImdbId, int limit) {
        return collaborativeFilteringService.topNeighbors(sourceImdbId, limit);
    }

    @Override
    public List<String> generateForUser(UserPreferenceProfile profile, int limit) {
        if (profile.getAnchorImdbIds() == null || profile.getAnchorImdbIds().isEmpty()) {
            return List.of();
        }

        // Aggregate collaborative neighbors across all anchors with position-decay weighting
        Map<String, Double> aggregatedScores = new HashMap<>();
        for (String anchor : profile.getAnchorImdbIds()) {
            List<String> neighbors = collaborativeFilteringService.topNeighbors(anchor, limit);
            for (int i = 0; i < neighbors.size(); i++) {
                String neighbor = neighbors.get(i);
                if (profile.getAnchorImdbIds().contains(neighbor)) {
                    continue;
                }
                aggregatedScores.merge(neighbor, 1.0 / (i + 1), Double::sum);
            }
        }

        return aggregatedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
