package com.MovieRecSys.Movie;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Generates candidates based on genre-level popularity.
 * For personalized requests, retrieves popular movies in the user's top genres.
 * This adds a popularity and exploration signal to the retrieval stage.
 */
@Component
public class PopularInGenreCandidateGenerator implements CandidateGenerator {
    private final MovieRepository movieRepository;

    public PopularInGenreCandidateGenerator(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @Override
    public String name() {
        return "popular-in-genre";
    }

    @Override
    public List<String> generateForMovie(String sourceImdbId, int limit) {
        return movieRepository.findMovieByImdbId(sourceImdbId)
                .map(movie -> {
                    if (movie.getGenres() == null || movie.getGenres().isEmpty()) {
                        return topPopular(limit, sourceImdbId);
                    }
                    String primaryGenre = movie.getGenres().get(0);
                    return movieRepository.searchCatalog(null, primaryGenre, 0, limit + 1)
                            .items().stream()
                            .map(Movie::getImdbId)
                            .filter(id -> !id.equals(sourceImdbId))
                            .limit(limit)
                            .toList();
                })
                .orElse(List.of());
    }

    @Override
    public List<String> generateForUser(UserPreferenceProfile profile, int limit) {
        if (profile.getGenreWeights() == null || profile.getGenreWeights().isEmpty()) {
            return topPopular(limit, null);
        }

        // Get top 3 genres from user profile
        List<String> topGenres = profile.getGenreWeights().entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(java.util.Map.Entry::getKey)
                .toList();

        Set<String> anchors = profile.getAnchorImdbIds() == null
                ? Set.of()
                : new LinkedHashSet<>(profile.getAnchorImdbIds());

        Set<String> candidates = new LinkedHashSet<>();
        int perGenre = Math.max(limit / topGenres.size(), 4);

        for (String genre : topGenres) {
            // Capitalize first letter for catalog search
            String normalizedGenre = genre.substring(0, 1).toUpperCase() + genre.substring(1);
            movieRepository.searchCatalog(null, normalizedGenre, 0, perGenre + anchors.size())
                    .items().stream()
                    .map(Movie::getImdbId)
                    .filter(id -> !anchors.contains(id))
                    .forEach(candidates::add);
        }

        return candidates.stream().limit(limit).toList();
    }

    private List<String> topPopular(int limit, String excludeImdbId) {
        return movieRepository.searchCatalog(null, null, 0, limit + 1)
                .items().stream()
                .map(Movie::getImdbId)
                .filter(id -> !id.equals(excludeImdbId))
                .limit(limit)
                .toList();
    }
}
