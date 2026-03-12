package com.MovieRecSys.Movie;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/**
 * Stage 2 ranker for the two-stage recommendation pipeline.
 * Computes a feature vector for each candidate and produces a total score.
 *
 * Feature weights:
 *   genre overlap    × 4.5  (strongest content signal)
 *   keyword overlap  × 3.5
 *   cast overlap     × 2.5
 *   director match   × 3.0
 *   collaborative    × 5.0  (strongest behavioral signal)
 *   rating affinity  × 0.35
 *   popularity bias  × 0.05  (capped at 50 ratings)
 *   recency boost    × 1.2
 *   exploration      × 0.8  (stochastic diversity injection)
 *   preference align × 1.0
 */
@Component
public class RankingScorer {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CollaborativeFilteringService collaborativeFilteringService;
    private final MovieSimilarityScorer contentScorer;

    public RankingScorer(
            CollaborativeFilteringService collaborativeFilteringService,
            MovieSimilarityScorer contentScorer
    ) {
        this.collaborativeFilteringService = collaborativeFilteringService;
        this.contentScorer = contentScorer;
    }

    /**
     * Score a candidate movie against a source movie (item-to-item).
     */
    public RankingFeatures scoreForMovie(Movie source, Movie candidate) {
        return RankingFeatures.builder(candidate.getImdbId())
                .genreOverlap(overlap(source.getGenres(), candidate.getGenres()) * 4.5)
                .keywordOverlap(overlap(source.getKeywords(), candidate.getKeywords()) * 3.5)
                .castOverlap(overlap(source.getCast(), candidate.getCast()) * 2.5)
                .directorMatch(directorMatch(source.getDirector(), candidate.getDirector()) * 3.0)
                .collaborativeSignal(
                        collaborativeFilteringService.signalStrength(source.getImdbId(), candidate.getImdbId()) * 5.0)
                .ratingAffinity(ratingAffinity(candidate) * 0.35)
                .popularityBias(popularityBias(candidate) * 0.05)
                .recencyBoost(recencyBoost(candidate) * 1.2)
                .explorationFactor(ThreadLocalRandom.current().nextDouble(0.0, 0.8))
                .preferenceAlignment(0.0) // no user context for item-to-item
                .build();
    }

    /**
     * Score a candidate movie in a personalized context.
     */
    public RankingFeatures scoreForUser(
            UserPreferenceProfile profile,
            List<Movie> anchors,
            Movie candidate
    ) {
        // Average content similarity across all anchor movies
        double avgGenre = anchors.stream()
                .mapToDouble(a -> overlap(a.getGenres(), candidate.getGenres()))
                .average().orElse(0.0);
        double avgKeyword = anchors.stream()
                .mapToDouble(a -> overlap(a.getKeywords(), candidate.getKeywords()))
                .average().orElse(0.0);
        double avgCast = anchors.stream()
                .mapToDouble(a -> overlap(a.getCast(), candidate.getCast()))
                .average().orElse(0.0);
        double avgDirector = anchors.stream()
                .mapToDouble(a -> directorMatch(a.getDirector(), candidate.getDirector()))
                .average().orElse(0.0);

        // Max collaborative signal across anchors
        double maxCollabSignal = anchors.stream()
                .mapToDouble(a -> collaborativeFilteringService.signalStrength(
                        a.getImdbId(), candidate.getImdbId()))
                .max().orElse(0.0);

        double preferenceScore = contentScorer.preferenceScore(profile, candidate);

        return RankingFeatures.builder(candidate.getImdbId())
                .genreOverlap(avgGenre * 4.5)
                .keywordOverlap(avgKeyword * 3.5)
                .castOverlap(avgCast * 2.5)
                .directorMatch(avgDirector * 3.0)
                .collaborativeSignal(maxCollabSignal * 5.0)
                .ratingAffinity(ratingAffinity(candidate) * 0.35)
                .popularityBias(popularityBias(candidate) * 0.05)
                .recencyBoost(recencyBoost(candidate) * 1.2)
                .explorationFactor(ThreadLocalRandom.current().nextDouble(0.0, 0.8))
                .preferenceAlignment(preferenceScore * 1.0)
                .build();
    }

    private double overlap(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> leftSet = new HashSet<>();
        left.forEach(v -> leftSet.add(v.toLowerCase()));
        Set<String> rightSet = new HashSet<>();
        right.forEach(v -> rightSet.add(v.toLowerCase()));

        Set<String> intersection = new HashSet<>(leftSet);
        intersection.retainAll(rightSet);
        Set<String> union = new HashSet<>(leftSet);
        union.addAll(rightSet);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double directorMatch(String left, String right) {
        if (left == null || right == null) return 0.0;
        return left.equalsIgnoreCase(right) ? 1.0 : 0.0;
    }

    private double ratingAffinity(Movie candidate) {
        return candidate.getAverageRating() == null ? 0.0 : candidate.getAverageRating();
    }

    private double popularityBias(Movie candidate) {
        return Math.min(candidate.getRatingCount() == null ? 0 : candidate.getRatingCount(), 50);
    }

    private double recencyBoost(Movie candidate) {
        if (candidate.getReleaseDate() == null || candidate.getReleaseDate().isBlank()) {
            return 0.0;
        }
        try {
            LocalDate releaseDate = LocalDate.parse(candidate.getReleaseDate().substring(0, 10), DATE_FORMAT);
            long daysSinceRelease = ChronoUnit.DAYS.between(releaseDate, LocalDate.now());
            if (daysSinceRelease <= 0) return 2.0; // unreleased or very recent
            return Math.max(0.0, 2.0 - Math.log10(daysSinceRelease) * 0.3);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
