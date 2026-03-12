package com.MovieRecSys.Movie;

import java.util.List;

/**
 * A candidate generator produces a set of candidate movie IMDb IDs
 * for a given context. Multiple generators are combined in the
 * retrieval stage of the two-stage recommendation pipeline.
 */
public interface CandidateGenerator {
    /**
     * A human-readable name for this generator, used in evaluation reports.
     */
    String name();

    /**
     * Generate candidate movie IMDb IDs for item-to-item recommendations.
     *
     * @param sourceImdbId the anchor movie
     * @param limit        maximum candidates to return
     * @return list of candidate IMDb IDs, ordered by generator-specific relevance
     */
    List<String> generateForMovie(String sourceImdbId, int limit);

    /**
     * Generate candidate movie IMDb IDs for personalized recommendations.
     *
     * @param profile the user's preference profile
     * @param limit   maximum candidates to return
     * @return list of candidate IMDb IDs, ordered by generator-specific relevance
     */
    List<String> generateForUser(UserPreferenceProfile profile, int limit);
}
