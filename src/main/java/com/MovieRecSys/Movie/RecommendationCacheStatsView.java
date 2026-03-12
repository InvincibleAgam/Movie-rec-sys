package com.MovieRecSys.Movie;

public record RecommendationCacheStatsView(
        boolean redisEnabled,
        long hits,
        long misses,
        long writes,
        long invalidations
) {
}
