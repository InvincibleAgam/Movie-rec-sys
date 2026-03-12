package com.MovieRecSys.Movie;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class MovieSimilarityScorer {
    public double totalScore(Movie source, Movie candidate) {
        return contentScore(source, candidate) + engagementBoost(candidate);
    }

    public double contentScore(Movie source, Movie candidate) {
        double score = 0.0;
        score += overlap(source.getGenres(), candidate.getGenres()) * 4.5;
        score += overlap(source.getKeywords(), candidate.getKeywords()) * 3.5;
        score += overlap(source.getCast(), candidate.getCast()) * 2.5;
        if (source.getDirector() != null && source.getDirector().equalsIgnoreCase(candidate.getDirector())) {
            score += 3.0;
        }
        return score;
    }

    public double engagementBoost(Movie candidate) {
        double score = 0.0;
        score += (candidate.getAverageRating() == null ? 0.0 : candidate.getAverageRating()) * 0.35;
        score += Math.min(candidate.getRatingCount() == null ? 0 : candidate.getRatingCount(), 50) * 0.05;
        return score;
    }

    public double preferenceScore(UserPreferenceProfile profile, Movie candidate) {
        if (profile == null) {
            return 0.0;
        }

        double score = 0.0;
        score += weightedAffinity(profile.getGenreWeights(), candidate.getGenres(), 1.8);
        score += weightedAffinity(profile.getKeywordWeights(), candidate.getKeywords(), 1.2);
        score += weightedAffinity(profile.getDirectorWeights(), candidate.getDirector() == null ? List.of() : List.of(candidate.getDirector()), 2.4);
        return score;
    }

    private double weightedAffinity(Map<String, Double> weights, List<String> values, double multiplier) {
        if (weights == null || weights.isEmpty() || values == null || values.isEmpty()) {
            return 0.0;
        }

        return values.stream()
                .map(String::toLowerCase)
                .mapToDouble(value -> weights.getOrDefault(value, 0.0))
                .sum() * multiplier;
    }

    private double overlap(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        Set<String> leftSet = left.stream().map(String::toLowerCase).collect(HashSet::new, HashSet::add, HashSet::addAll);
        Set<String> rightSet = right.stream().map(String::toLowerCase).collect(HashSet::new, HashSet::add, HashSet::addAll);

        Set<String> intersection = new HashSet<>(leftSet);
        intersection.retainAll(rightSet);
        Set<String> union = new HashSet<>(leftSet);
        union.addAll(rightSet);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
