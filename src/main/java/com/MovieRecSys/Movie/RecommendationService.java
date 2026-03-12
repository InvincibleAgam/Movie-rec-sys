package com.MovieRecSys.Movie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {
    private final MovieRepository movieRepository;
    private final AuthService authService;
    private final RecommendationSnapshotService recommendationSnapshotService;
    private final MovieSimilarityScorer movieSimilarityScorer;
    private final UserPreferenceProfileRepository userPreferenceProfileRepository;
    private final RecommendationProfileProjector recommendationProfileProjector;
    private final RecommendationCacheService recommendationCacheService;

    public RecommendationService(
            MovieRepository movieRepository,
            AuthService authService,
            RecommendationSnapshotService recommendationSnapshotService,
            MovieSimilarityScorer movieSimilarityScorer,
            UserPreferenceProfileRepository userPreferenceProfileRepository,
            RecommendationProfileProjector recommendationProfileProjector,
            RecommendationCacheService recommendationCacheService
    ) {
        this.movieRepository = movieRepository;
        this.authService = authService;
        this.recommendationSnapshotService = recommendationSnapshotService;
        this.movieSimilarityScorer = movieSimilarityScorer;
        this.userPreferenceProfileRepository = userPreferenceProfileRepository;
        this.recommendationProfileProjector = recommendationProfileProjector;
        this.recommendationCacheService = recommendationCacheService;
    }

    public List<Movie> recommendationsForMovie(String imdbId, int limit) {
        String cacheKey = recommendationCacheService.movieRecommendationKey(imdbId, limit);
        Optional<List<String>> cachedIds = recommendationCacheService.getRecommendationIds(cacheKey);
        if (cachedIds.isPresent()) {
            return hydrateMoviesInOrder(cachedIds.get());
        }

        Movie targetMovie = movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));

        List<Movie> precomputed = precomputedRecommendations(targetMovie, limit);
        List<Movie> recommendations = !precomputed.isEmpty()
                ? precomputed
                : scoreCandidates(targetMovie, movieRepository.findAll(), limit, Set.of(targetMovie.getImdbId()));

        recommendationCacheService.cacheRecommendationIds(cacheKey, imdbIds(recommendations), false);
        return recommendations;
    }

    public List<Movie> personalizedRecommendations(String authorizationHeader, int limit) {
        AppUser user = authService.requireUser(authorizationHeader);
        String cacheKey = recommendationCacheService.personalizedRecommendationKey(user.getId(), limit);
        Optional<List<String>> cachedIds = recommendationCacheService.getRecommendationIds(cacheKey);
        if (cachedIds.isPresent()) {
            return hydrateMoviesInOrder(cachedIds.get());
        }

        UserPreferenceProfile profile = userPreferenceProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> recommendationProfileProjector.rebuildProfile(user.getId()));
        Set<String> anchorImdbIds = new HashSet<>(profile.getAnchorImdbIds() == null ? List.of() : profile.getAnchorImdbIds());

        if (anchorImdbIds.isEmpty()) {
            List<Movie> popular = movieRepository.searchCatalog(null, null, 0, limit).items();
            recommendationCacheService.cacheRecommendationIds(cacheKey, imdbIds(popular), true);
            return popular;
        }

        List<Movie> anchors = movieRepository.findByImdbIdIn(new ArrayList<>(anchorImdbIds));
        List<Movie> candidatePool = personalizedCandidatePool(anchorImdbIds, Math.max(limit * 4, 24));
        if (candidatePool.isEmpty()) {
            candidatePool = movieRepository.findAll();
        }

        List<Movie> recommendations = candidatePool.stream()
                .filter(movie -> !anchorImdbIds.contains(movie.getImdbId()))
                .sorted(Comparator.comparingDouble((Movie movie) -> personalizedScore(profile, anchors, movie)).reversed())
                .limit(limit)
                .toList();
        recommendationCacheService.cacheRecommendationIds(cacheKey, imdbIds(recommendations), true);
        return recommendations;
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

    private List<Movie> personalizedCandidatePool(Set<String> anchorImdbIds, int candidateLimit) {
        Map<String, Double> candidateWeights = new HashMap<>();
        for (String anchorImdbId : anchorImdbIds) {
            List<String> candidates = recommendationSnapshotService.topCandidatesForMovie(anchorImdbId, candidateLimit);
            for (int index = 0; index < candidates.size(); index++) {
                String candidateImdbId = candidates.get(index);
                if (anchorImdbIds.contains(candidateImdbId)) {
                    continue;
                }
                candidateWeights.merge(candidateImdbId, 1.0 / (index + 1), Double::sum);
            }
        }

        if (candidateWeights.isEmpty()) {
            return List.of();
        }

        Map<String, Movie> moviesByImdbId = movieRepository.findByImdbIdIn(new ArrayList<>(candidateWeights.keySet())).stream()
                .collect(Collectors.toMap(Movie::getImdbId, Function.identity()));

        return candidateWeights.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> moviesByImdbId.get(entry.getKey()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<Movie> precomputedRecommendations(Movie targetMovie, int limit) {
        List<String> candidateIds = recommendationSnapshotService.topCandidatesForMovie(targetMovie.getImdbId(), Math.max(limit * 2, limit));
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Map<String, Movie> moviesByImdbId = movieRepository.findByImdbIdIn(candidateIds).stream()
                .collect(Collectors.toMap(Movie::getImdbId, Function.identity()));

        return candidateIds.stream()
                .map(moviesByImdbId::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble((Movie movie) -> movieSimilarityScorer.totalScore(targetMovie, movie)).reversed())
                .limit(limit)
                .toList();
    }

    private List<Movie> scoreCandidates(Movie source, List<Movie> candidates, int limit, Set<String> excludedImdbIds) {
        return candidates.stream()
                .filter(movie -> !excludedImdbIds.contains(movie.getImdbId()))
                .sorted(Comparator.comparingDouble((Movie movie) -> movieSimilarityScorer.totalScore(source, movie)).reversed())
                .limit(limit)
                .toList();
    }

    private double personalizedScore(UserPreferenceProfile profile, List<Movie> anchors, Movie movie) {
        double anchorScore = anchors.stream()
                .mapToDouble(anchor -> movieSimilarityScorer.contentScore(anchor, movie))
                .average()
                .orElse(0.0);
        return anchorScore + movieSimilarityScorer.preferenceScore(profile, movie) + movieSimilarityScorer.engagementBoost(movie);
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
