package com.MovieRecSys.Movie;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Generates candidates from precomputed content-similarity snapshots.
 * This is the fastest retrieval path — O(1) lookup per anchor movie.
 */
@Component
public class ContentSimilarityCandidateGenerator implements CandidateGenerator {
    private final RecommendationSnapshotService snapshotService;

    public ContentSimilarityCandidateGenerator(RecommendationSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @Override
    public String name() {
        return "content-similarity";
    }

    @Override
    public List<String> generateForMovie(String sourceImdbId, int limit) {
        return snapshotService.topCandidatesForMovie(sourceImdbId, limit);
    }

    @Override
    public List<String> generateForUser(UserPreferenceProfile profile, int limit) {
        if (profile.getAnchorImdbIds() == null || profile.getAnchorImdbIds().isEmpty()) {
            return List.of();
        }

        // Fan out from each anchor and merge candidates
        int perAnchor = Math.max(limit / profile.getAnchorImdbIds().size(), 4);
        return profile.getAnchorImdbIds().stream()
                .flatMap(anchor -> snapshotService.topCandidatesForMovie(anchor, perAnchor).stream())
                .distinct()
                .limit(limit)
                .toList();
    }
}
