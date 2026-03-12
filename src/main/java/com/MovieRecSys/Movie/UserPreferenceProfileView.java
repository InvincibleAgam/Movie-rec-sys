package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public record UserPreferenceProfileView(
        int anchorCount,
        Map<String, Double> topGenres,
        Map<String, Double> topKeywords,
        Map<String, Double> topDirectors,
        Instant updatedAt,
        long sourceEventCount
) {
    public static UserPreferenceProfileView from(UserPreferenceProfile profile) {
        return new UserPreferenceProfileView(
                profile.getAnchorImdbIds() == null ? 0 : profile.getAnchorImdbIds().size(),
                topEntries(profile.getGenreWeights(), 5),
                topEntries(profile.getKeywordWeights(), 5),
                topEntries(profile.getDirectorWeights(), 5),
                profile.getUpdatedAt(),
                profile.getSourceEventCount()
        );
    }

    private static Map<String, Double> topEntries(Map<String, Double> values, int limit) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        return values.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Math.round(entry.getValue() * 100.0) / 100.0,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }
}
