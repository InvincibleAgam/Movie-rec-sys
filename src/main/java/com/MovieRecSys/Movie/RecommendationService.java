package com.MovieRecSys.Movie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {
    private final MovieRepository movieRepository;
    private final RatingRepository ratingRepository;
    private final AuthService authService;

    public RecommendationService(MovieRepository movieRepository, RatingRepository ratingRepository, AuthService authService) {
        this.movieRepository = movieRepository;
        this.ratingRepository = ratingRepository;
        this.authService = authService;
    }

    public List<Movie> recommendationsForMovie(String imdbId, int limit) {
        Movie targetMovie = movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));

        return movieRepository.findAll().stream()
                .filter(movie -> !movie.getImdbId().equals(targetMovie.getImdbId()))
                .sorted(Comparator.comparingDouble((Movie movie) -> score(targetMovie, movie)).reversed())
                .limit(limit)
                .toList();
    }

    public List<Movie> personalizedRecommendations(String authorizationHeader, int limit) {
        AppUser user = authService.requireUser(authorizationHeader);
        Set<String> anchorImdbIds = new HashSet<>(user.getWatchlistImdbIds() == null ? List.of() : user.getWatchlistImdbIds());
        ratingRepository.findByUserId(user.getId()).stream()
                .filter(rating -> rating.getValue() >= 4)
                .map(Rating::getImdbId)
                .forEach(anchorImdbIds::add);

        if (anchorImdbIds.isEmpty()) {
            return movieRepository.findAll().stream()
                    .sorted(Comparator.comparing(Movie::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(Movie::getRatingCount, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(limit)
                    .toList();
        }

        List<Movie> anchors = movieRepository.findByImdbIdIn(new ArrayList<>(anchorImdbIds));
        return movieRepository.findAll().stream()
                .filter(movie -> !anchorImdbIds.contains(movie.getImdbId()))
                .sorted(Comparator.comparingDouble((Movie movie) -> anchors.stream()
                        .mapToDouble(anchor -> score(anchor, movie))
                        .average()
                        .orElse(0.0)).reversed())
                .limit(limit)
                .toList();
    }

    private double score(Movie source, Movie candidate) {
        double score = 0.0;
        score += overlap(source.getGenres(), candidate.getGenres()) * 4.5;
        score += overlap(source.getKeywords(), candidate.getKeywords()) * 3.5;
        score += overlap(source.getCast(), candidate.getCast()) * 2.5;
        if (source.getDirector() != null && source.getDirector().equalsIgnoreCase(candidate.getDirector())) {
            score += 3.0;
        }
        score += (candidate.getAverageRating() == null ? 0.0 : candidate.getAverageRating()) * 0.35;
        score += Math.min(candidate.getRatingCount() == null ? 0 : candidate.getRatingCount(), 50) * 0.05;
        return score;
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
