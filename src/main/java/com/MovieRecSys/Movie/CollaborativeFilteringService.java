package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Builds item-item collaborative filtering signals from co-watch and co-rate data.
 * Uses an inverse-user-frequency weighting: shared signals from users with fewer
 * interactions are worth more than signals from hyper-active users.
 */
@Service
public class CollaborativeFilteringService {
    private static final Logger log = LoggerFactory.getLogger(CollaborativeFilteringService.class);

    private final RatingRepository ratingRepository;
    private final AppUserRepository appUserRepository;
    private final CollaborativeSignalRepository collaborativeSignalRepository;
    private final int maxNeighbors;
    private final boolean enabled;

    public CollaborativeFilteringService(
            RatingRepository ratingRepository,
            AppUserRepository appUserRepository,
            MovieRepository movieRepository,
            CollaborativeSignalRepository collaborativeSignalRepository,
            @Value("${app.collaborative.max-neighbors:30}") int maxNeighbors,
            @Value("${app.collaborative.enabled:true}") boolean enabled
    ) {
        this.ratingRepository = ratingRepository;
        this.appUserRepository = appUserRepository;
        this.collaborativeSignalRepository = collaborativeSignalRepository;
        this.maxNeighbors = maxNeighbors;
        this.enabled = enabled;
    }

    /**
     * Periodic rebuild of the co-occurrence matrix.
     * Runs every 30 minutes by default.
     */
    @Scheduled(fixedDelayString = "${app.collaborative.rebuild-delay-ms:1800000}")
    public void rebuildOnSchedule() {
        if (!enabled) {
            return;
        }
        rebuildAll();
    }

    /**
     * Full rebuild of collaborative signals from all ratings and watchlists.
     *
     * Algorithm:
     * 1. Build a user->items map from ratings + watchlists.
     * 2. For each pair of items that share at least one user, compute:
     *    score += 1.0 / log2(2 + |user's total items|)
     *    This is the inverse-user-frequency weight that down-weights
     *    users who interact with many items (they are less discriminative).
     * 3. If both users rated both items, apply a rating-agreement bonus:
     *    if ratings are within 1 point, multiply contribution by 1.5.
     */
    public void rebuildAll() {
        log.info("Starting collaborative signal rebuild");
        long startTime = System.currentTimeMillis();

        // Step 1: Build user -> rated/watchlisted items map
        Map<String, Set<String>> userItemSets = new HashMap<>();
        Map<String, Map<String, Integer>> userRatings = new HashMap<>();

        List<Rating> allRatings = ratingRepository.findAll();
        for (Rating rating : allRatings) {
            String userId = rating.getUserId().toHexString();
            userItemSets.computeIfAbsent(userId, k -> new HashSet<>()).add(rating.getImdbId());
            userRatings.computeIfAbsent(userId, k -> new HashMap<>())
                    .put(rating.getImdbId(), rating.getValue());
        }

        List<AppUser> allUsers = appUserRepository.findAll();
        for (AppUser user : allUsers) {
            if (user.getWatchlistImdbIds() == null || user.getWatchlistImdbIds().isEmpty()) {
                continue;
            }
            String userId = user.getId().toHexString();
            userItemSets.computeIfAbsent(userId, k -> new HashSet<>())
                    .addAll(user.getWatchlistImdbIds());
        }

        // Step 2: Compute co-occurrence with inverse-user-frequency
        Map<String, Map<String, Double>> coOccurrence = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : userItemSets.entrySet()) {
            String userId = entry.getKey();
            List<String> items = new ArrayList<>(entry.getValue());
            double userWeight = 1.0 / (Math.log(2 + items.size()) / Math.log(2));
            Map<String, Integer> thisUserRatings = userRatings.getOrDefault(userId, Map.of());

            for (int i = 0; i < items.size(); i++) {
                for (int j = i + 1; j < items.size(); j++) {
                    String itemA = items.get(i);
                    String itemB = items.get(j);
                    double weight = userWeight;

                    // Rating agreement bonus
                    Integer ratingA = thisUserRatings.get(itemA);
                    Integer ratingB = thisUserRatings.get(itemB);
                    if (ratingA != null && ratingB != null) {
                        if (Math.abs(ratingA - ratingB) <= 1 && ratingA >= 4) {
                            weight *= 1.5;
                        }
                    }

                    coOccurrence.computeIfAbsent(itemA, k -> new HashMap<>())
                            .merge(itemB, weight, Double::sum);
                    coOccurrence.computeIfAbsent(itemB, k -> new HashMap<>())
                            .merge(itemA, weight, Double::sum);
                }
            }
        }

        // Step 3: Persist top-K neighbors per item
        collaborativeSignalRepository.deleteAll();
        List<CollaborativeSignal> signals = new ArrayList<>();

        for (Map.Entry<String, Map<String, Double>> entry : coOccurrence.entrySet()) {
            CollaborativeSignal signal = new CollaborativeSignal();
            signal.setImdbId(entry.getKey());
            signal.setNeighborScores(topK(entry.getValue(), maxNeighbors));
            signal.setComputedAt(Instant.now());
            signals.add(signal);
        }

        collaborativeSignalRepository.saveAll(signals);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Collaborative signal rebuild complete: {} items in {} ms", signals.size(), elapsed);
    }

    /**
     * Retrieve the top collaborative neighbors for a given movie.
     */
    public List<String> topNeighbors(String imdbId, int limit) {
        return collaborativeSignalRepository.findByImdbId(imdbId)
                .map(signal -> signal.getNeighborScores().entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(limit)
                        .map(Map.Entry::getKey)
                        .toList())
                .orElse(List.of());
    }

    /**
     * Get the collaborative score between two movies.
     */
    public double signalStrength(String sourceImdbId, String candidateImdbId) {
        return collaborativeSignalRepository.findByImdbId(sourceImdbId)
                .map(signal -> signal.getNeighborScores().getOrDefault(candidateImdbId, 0.0))
                .orElse(0.0);
    }

    private Map<String, Double> topK(Map<String, Double> scores, int k) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(k)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }
}
