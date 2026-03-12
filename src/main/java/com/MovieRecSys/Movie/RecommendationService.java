package com.MovieRecSys.Movie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Two-stage recommendation service:
 *   Stage 1 (Retrieval): Multiple CandidateGenerators produce a broad pool of candidates.
 *   Stage 2 (Ranking):   RankingScorer scores each candidate using 10 features.
 *
 * Caching is applied at the final output level.
 */
@Service
public class RecommendationService {

    private final MovieRepository movieRepository;
    private final AuthService authService;
    private final RankingScorer rankingScorer;
    private final List<CandidateGenerator> candidateGenerators;
    private final UserPreferenceProfileRepository userPreferenceProfileRepository;
    private final RecommendationProfileProjector recommendationProfileProjector;
    private final RecommendationCacheService recommendationCacheService;

    public RecommendationService(
            MovieRepository movieRepository,
            AuthService authService,
            RankingScorer rankingScorer,
            List<CandidateGenerator> candidateGenerators,
            UserPreferenceProfileRepository userPreferenceProfileRepository,
            RecommendationProfileProjector recommendationProfileProjector,
            RecommendationCacheService recommendationCacheService
    ) {
        this.movieRepository = movieRepository;
        this.authService = authService;
        this.rankingScorer = rankingScorer;
        this.candidateGenerators = candidateGenerators;
        this.userPreferenceProfileRepository = userPreferenceProfileRepository;
        this.recommendationProfileProjector = recommendationProfileProjector;
        this.recommendationCacheService = recommendationCacheService;
    }

    /**
     * Item-to-item recommendations using two-stage pipeline.
     */
    public List<Movie> recommendationsForMovie(String imdbId, int limit) {
        String cacheKey = recommendationCacheService.movieRecommendationKey(imdbId, limit);
        Optional<List<String>> cachedIds = recommendationCacheService.getRecommendationIds(cacheKey);
        if (cachedIds.isPresent()) {
            return hydrateMoviesInOrder(cachedIds.get());
        }

        Movie targetMovie = movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));

        // Stage 1: Retrieval — gather candidates from all generators
        Set<String> candidateIds = new LinkedHashSet<>();
        int candidatePoolSize = Math.max(limit * 4, 24);
        for (CandidateGenerator generator : candidateGenerators) {
            candidateIds.addAll(generator.generateForMovie(imdbId, candidatePoolSize));
        }
        candidateIds.remove(imdbId);

        // Fallback: if all generators return empty, scan full catalog
        List<Movie> candidates;
        if (candidateIds.isEmpty()) {
            candidates = movieRepository.findAll().stream()
                    .filter(m -> !m.getImdbId().equals(imdbId))
                    .toList();
        } else {
            candidates = movieRepository.findByImdbIdIn(new ArrayList<>(candidateIds)).stream()
                    .filter(m -> !m.getImdbId().equals(imdbId))
                    .toList();
        }

        // Stage 2: Ranking — score each candidate with full feature vector
        List<Movie> ranked = rankAndSelect(targetMovie, candidates, limit);
        recommendationCacheService.cacheRecommendationIds(cacheKey, imdbIds(ranked), false);
        return ranked;
    }

    /**
     * Personalized recommendations using two-stage pipeline.
     */
    public List<Movie> personalizedRecommendations(String authorizationHeader, int limit) {
        AppUser user = authService.requireUser(authorizationHeader);
        String cacheKey = recommendationCacheService.personalizedRecommendationKey(user.getId(), limit);
        Optional<List<String>> cachedIds = recommendationCacheService.getRecommendationIds(cacheKey);
        if (cachedIds.isPresent()) {
            return hydrateMoviesInOrder(cachedIds.get());
        }

        UserPreferenceProfile profile = userPreferenceProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> recommendationProfileProjector.rebuildProfile(user.getId()));
        Set<String> anchorImdbIds = new HashSet<>(
                profile.getAnchorImdbIds() == null ? List.of() : profile.getAnchorImdbIds());

        if (anchorImdbIds.isEmpty()) {
            List<Movie> popular = movieRepository.searchCatalog(null, null, 0, limit).items();
            recommendationCacheService.cacheRecommendationIds(cacheKey, imdbIds(popular), true);
            return popular;
        }

        // Stage 1: Retrieval — gather candidates from all generators
        Set<String> candidateIds = new LinkedHashSet<>();
        int candidatePoolSize = Math.max(limit * 4, 24);
        for (CandidateGenerator generator : candidateGenerators) {
            candidateIds.addAll(generator.generateForUser(profile, candidatePoolSize));
        }
        candidateIds.removeAll(anchorImdbIds);

        List<Movie> candidates;
        if (candidateIds.isEmpty()) {
            candidates = movieRepository.findAll();
        } else {
            candidates = movieRepository.findByImdbIdIn(new ArrayList<>(candidateIds));
        }

        List<Movie> anchors = movieRepository.findByImdbIdIn(new ArrayList<>(anchorImdbIds));

        // Stage 2: Ranking — score with user preference context
        List<Movie> ranked = candidates.stream()
                .filter(m -> !anchorImdbIds.contains(m.getImdbId()))
                .map(m -> rankingScorer.scoreForUser(profile, anchors, m))
                .sorted(Comparator.comparingDouble(RankingFeatures::totalScore).reversed())
                .limit(limit)
                .map(features -> candidates.stream()
                        .filter(m -> m.getImdbId().equals(features.imdbId()))
                        .findFirst().orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        recommendationCacheService.cacheRecommendationIds(cacheKey, imdbIds(ranked), true);
        return ranked;
    }

    public UserPreferenceProfileView profileView(String authorizationHeader) {
        AppUser user = authService.requireUser(authorizationHeader);
        String cacheKey = recommendationCacheService.profileViewKey(user.getId());
        Optional<UserPreferenceProfileView> cachedView = recommendationCacheService.getProfileView(cacheKey);
        if (cachedView.isPresent()) {
            return cachedView.get();
        }

        UserPreferenceProfile profile = userPreferenceProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> recommendationProfileProjector.rebuildProfile(user.getId()));
        UserPreferenceProfileView view = UserPreferenceProfileView.from(profile);
        recommendationCacheService.cacheProfileView(cacheKey, view);
        return view;
    }

    public RecommendationCacheStatsView cacheStats() {
        return recommendationCacheService.statsView();
    }

    private List<Movie> rankAndSelect(Movie source, List<Movie> candidates, int limit) {
        Map<String, Movie> moviesByImdbId = candidates.stream()
                .collect(Collectors.toMap(Movie::getImdbId, Function.identity(), (a, b) -> a));

        return candidates.stream()
                .map(candidate -> rankingScorer.scoreForMovie(source, candidate))
                .sorted(Comparator.comparingDouble(RankingFeatures::totalScore).reversed())
                .limit(limit)
                .map(features -> moviesByImdbId.get(features.imdbId()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<Movie> hydrateMoviesInOrder(List<String> imdbIds) {
        if (imdbIds == null || imdbIds.isEmpty()) {
            return List.of();
        }

        Map<String, Movie> moviesByImdbId = movieRepository.findByImdbIdIn(imdbIds).stream()
                .collect(Collectors.toMap(Movie::getImdbId, Function.identity()));
        return imdbIds.stream()
                .map(moviesByImdbId::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<String> imdbIds(List<Movie> movies) {
        return movies.stream()
                .map(Movie::getImdbId)
                .toList();
    }
}
