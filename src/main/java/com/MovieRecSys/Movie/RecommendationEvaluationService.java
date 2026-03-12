package com.MovieRecSys.Movie;


import java.util.Comparator;
import java.util.HashMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Offline evaluation pipeline for the recommendation system.
 * Computes NDCG@K, Precision@K, Recall@K, and MAP using a temporal
 * train/test split of user ratings.
 *
 * Methodology:
 *   - For each user with >= 4 ratings, split chronologically:
 *     Train = first 70%, Test = last 30%
 *   - Use train ratings to build user profile
 *   - Generate recommendations using different strategies
 *   - Compare against test ratings (relevant = rating >= 4)
 *   - Report averaged metrics per strategy
 */
@Service
public class RecommendationEvaluationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationEvaluationService.class);
    private static final int MIN_RATINGS_FOR_EVAL = 4;
    private static final double RELEVANCE_THRESHOLD = 4.0;

    private final RatingRepository ratingRepository;
    private final AppUserRepository appUserRepository;
    private final MovieRepository movieRepository;
    private final MovieSimilarityScorer contentScorer;
    private final RankingScorer rankingScorer;
    private final List<CandidateGenerator> candidateGenerators;
    private final RecommendationSnapshotService snapshotService;
    private final RecommendationProfileProjector profileProjector;

    public RecommendationEvaluationService(
            RatingRepository ratingRepository,
            AppUserRepository appUserRepository,
            MovieRepository movieRepository,
            MovieSimilarityScorer contentScorer,
            RankingScorer rankingScorer,
            List<CandidateGenerator> candidateGenerators,
            RecommendationSnapshotService snapshotService,
            RecommendationProfileProjector profileProjector
    ) {
        this.ratingRepository = ratingRepository;
        this.appUserRepository = appUserRepository;
        this.movieRepository = movieRepository;
        this.contentScorer = contentScorer;
        this.rankingScorer = rankingScorer;
        this.candidateGenerators = candidateGenerators;
        this.snapshotService = snapshotService;
        this.profileProjector = profileProjector;
    }

    /**
     * Run a full evaluation cycle and return metrics per strategy.
     *
     * @param k the cutoff for @K metrics
     */
    public EvaluationReport evaluate(int k) {
        log.info("Starting evaluation with k={}", k);
        long startTime = System.currentTimeMillis();

        List<AppUser> users = appUserRepository.findAll();
        Map<String, Movie> allMovies = movieRepository.findAll().stream()
                .collect(Collectors.toMap(Movie::getImdbId, Function.identity()));

        // Define strategies to compare
        List<EvaluationStrategy> strategies = List.of(
                new EvaluationStrategy("content-only", this::contentOnlyScore),
                new EvaluationStrategy("content+engagement", this::contentEngagementScore),
                new EvaluationStrategy("full-pipeline (content+collab+ranking)", this::fullPipelineScore)
        );

        Map<String, StrategyMetrics> results = new LinkedHashMap<>();
        for (EvaluationStrategy strategy : strategies) {
            results.put(strategy.name(), new StrategyMetrics());
        }

        int evaluatedUsers = 0;

        for (AppUser user : users) {
            List<Rating> userRatings = ratingRepository.findByUserId(user.getId()).stream()
                    .sorted(Comparator.comparing(Rating::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();

            if (userRatings.size() < MIN_RATINGS_FOR_EVAL) {
                continue;
            }

            int splitPoint = (int) (userRatings.size() * 0.7);
            List<Rating> trainRatings = userRatings.subList(0, splitPoint);
            List<Rating> testRatings = userRatings.subList(splitPoint, userRatings.size());

            // Build ground truth: items in test set with rating >= threshold
            Set<String> relevantItems = testRatings.stream()
                    .filter(r -> r.getValue() >= RELEVANCE_THRESHOLD)
                    .map(Rating::getImdbId)
                    .collect(Collectors.toSet());

            if (relevantItems.isEmpty()) {
                continue;
            }

            // Build anchor movies from train ratings
            List<Movie> anchors = trainRatings.stream()
                    .filter(r -> r.getValue() >= RELEVANCE_THRESHOLD)
                    .map(r -> allMovies.get(r.getImdbId()))
                    .filter(java.util.Objects::nonNull)
                    .toList();

            if (anchors.isEmpty()) {
                continue;
            }

            Set<String> trainItemIds = trainRatings.stream()
                    .map(Rating::getImdbId)
                    .collect(Collectors.toSet());

            // Score each strategy
            for (EvaluationStrategy strategy : strategies) {
                List<String> ranked = strategy.scorer().rank(anchors, allMovies, trainItemIds, k);
                double ndcg = computeNDCG(ranked, relevantItems, k);
                double precision = computePrecisionAtK(ranked, relevantItems, k);
                double recall = computeRecallAtK(ranked, relevantItems, k);
                double ap = computeAveragePrecision(ranked, relevantItems, k);

                StrategyMetrics metrics = results.get(strategy.name());
                metrics.addNDCG(ndcg);
                metrics.addPrecision(precision);
                metrics.addRecall(recall);
                metrics.addAP(ap);
            }

            evaluatedUsers++;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Evaluation complete: {} users evaluated in {} ms", evaluatedUsers, elapsed);

        Map<String, StrategyReport> strategyReports = new LinkedHashMap<>();
        for (Map.Entry<String, StrategyMetrics> entry : results.entrySet()) {
            StrategyMetrics m = entry.getValue();
            strategyReports.put(entry.getKey(), new StrategyReport(
                    m.avgNDCG(), m.avgPrecision(), m.avgRecall(), m.avgAP(), m.count()
            ));
        }

        return new EvaluationReport(k, evaluatedUsers, elapsed, strategyReports);
    }

    // ---------- Scoring strategies ----------

    private List<String> contentOnlyScore(List<Movie> anchors, Map<String, Movie> allMovies,
                                          Set<String> excludeIds, int k) {
        return allMovies.values().stream()
                .filter(m -> !excludeIds.contains(m.getImdbId()))
                .sorted(Comparator.comparingDouble((Movie m) ->
                                anchors.stream()
                                        .mapToDouble(a -> contentScorer.contentScore(a, m))
                                        .average().orElse(0.0))
                        .reversed())
                .limit(k)
                .map(Movie::getImdbId)
                .toList();
    }

    private List<String> contentEngagementScore(List<Movie> anchors, Map<String, Movie> allMovies,
                                                Set<String> excludeIds, int k) {
        return allMovies.values().stream()
                .filter(m -> !excludeIds.contains(m.getImdbId()))
                .sorted(Comparator.comparingDouble((Movie m) ->
                                anchors.stream()
                                        .mapToDouble(a -> contentScorer.totalScore(a, m))
                                        .average().orElse(0.0))
                        .reversed())
                .limit(k)
                .map(Movie::getImdbId)
                .toList();
    }

    private List<String> fullPipelineScore(List<Movie> anchors, Map<String, Movie> allMovies,
                                           Set<String> excludeIds, int k) {
        // Build a mock profile from anchors
        UserPreferenceProfile mockProfile = new UserPreferenceProfile();
        mockProfile.setAnchorImdbIds(anchors.stream().map(Movie::getImdbId).toList());

        Map<String, Double> genreWeights = new HashMap<>();
        Map<String, Double> keywordWeights = new HashMap<>();
        Map<String, Double> directorWeights = new HashMap<>();

        for (Movie anchor : anchors) {
            if (anchor.getGenres() != null) {
                anchor.getGenres().forEach(g -> genreWeights.merge(g.toLowerCase(), 1.0, Double::sum));
            }
            if (anchor.getKeywords() != null) {
                anchor.getKeywords().forEach(kw -> keywordWeights.merge(kw.toLowerCase(), 0.7, Double::sum));
            }
            if (anchor.getDirector() != null) {
                directorWeights.merge(anchor.getDirector().toLowerCase(), 1.2, Double::sum);
            }
        }
        mockProfile.setGenreWeights(genreWeights);
        mockProfile.setKeywordWeights(keywordWeights);
        mockProfile.setDirectorWeights(directorWeights);

        return allMovies.values().stream()
                .filter(m -> !excludeIds.contains(m.getImdbId()))
                .map(m -> rankingScorer.scoreForUser(mockProfile, anchors, m))
                .sorted(Comparator.comparingDouble(RankingFeatures::totalScore).reversed())
                .limit(k)
                .map(RankingFeatures::imdbId)
                .toList();
    }

    // ---------- Metrics ----------

    private double computeNDCG(List<String> ranked, Set<String> relevant, int k) {
        double dcg = 0.0;
        double idcg = 0.0;
        int relevantCount = Math.min(relevant.size(), k);

        for (int i = 0; i < Math.min(ranked.size(), k); i++) {
            double gain = relevant.contains(ranked.get(i)) ? 1.0 : 0.0;
            dcg += gain / (Math.log(i + 2) / Math.log(2)); // log2(i+2)
        }

        for (int i = 0; i < relevantCount; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    private double computePrecisionAtK(List<String> ranked, Set<String> relevant, int k) {
        long hits = ranked.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / k;
    }

    private double computeRecallAtK(List<String> ranked, Set<String> relevant, int k) {
        if (relevant.isEmpty()) return 0.0;
        long hits = ranked.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / relevant.size();
    }

    private double computeAveragePrecision(List<String> ranked, Set<String> relevant, int k) {
        double sumPrecision = 0.0;
        int relevantSoFar = 0;

        for (int i = 0; i < Math.min(ranked.size(), k); i++) {
            if (relevant.contains(ranked.get(i))) {
                relevantSoFar++;
                sumPrecision += (double) relevantSoFar / (i + 1);
            }
        }

        return relevant.isEmpty() ? 0.0 : sumPrecision / relevant.size();
    }

    // ---------- Helper types ----------

    @FunctionalInterface
    interface RankingStrategy {
        List<String> rank(List<Movie> anchors, Map<String, Movie> allMovies, Set<String> excludeIds, int k);
    }

    record EvaluationStrategy(String name, RankingStrategy scorer) {}

    public record EvaluationReport(
            int k,
            int evaluatedUsers,
            long elapsedMs,
            Map<String, StrategyReport> strategies
    ) {}

    public record StrategyReport(
            double ndcg,
            double precisionAtK,
            double recallAtK,
            double meanAveragePrecision,
            int userCount
    ) {}

    private static class StrategyMetrics {
        private double totalNDCG;
        private double totalPrecision;
        private double totalRecall;
        private double totalAP;
        private int count;

        void addNDCG(double v) { totalNDCG += v; count++; }
        void addPrecision(double v) { totalPrecision += v; }
        void addRecall(double v) { totalRecall += v; }
        void addAP(double v) { totalAP += v; }

        double avgNDCG() { return count == 0 ? 0 : totalNDCG / count; }
        double avgPrecision() { return count == 0 ? 0 : totalPrecision / count; }
        double avgRecall() { return count == 0 ? 0 : totalRecall / count; }
        double avgAP() { return count == 0 ? 0 : totalAP / count; }
        int count() { return count; }
    }
}
