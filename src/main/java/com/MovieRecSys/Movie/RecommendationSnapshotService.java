package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecommendationSnapshotService {
    private final MovieRepository movieRepository;
    private final MovieRecommendationSnapshotRepository snapshotRepository;
    private final MovieSimilarityScorer movieSimilarityScorer;
    private final int snapshotSize;

    public RecommendationSnapshotService(
            MovieRepository movieRepository,
            MovieRecommendationSnapshotRepository snapshotRepository,
            MovieSimilarityScorer movieSimilarityScorer,
            @Value("${app.recommendations.snapshot-size:24}") int snapshotSize
    ) {
        this.movieRepository = movieRepository;
        this.snapshotRepository = snapshotRepository;
        this.movieSimilarityScorer = movieSimilarityScorer;
        this.snapshotSize = snapshotSize;
    }

    public void rebuildIfOutdated() {
        long movieCount = movieRepository.count();
        long snapshotCount = snapshotRepository.count();
        if (movieCount == 0 || movieCount == snapshotCount) {
            return;
        }
        rebuildAll();
    }

    public void rebuildAll() {
        List<Movie> catalog = movieRepository.findAll();
        if (catalog.isEmpty()) {
            return;
        }

        snapshotRepository.deleteAll();
        snapshotRepository.saveAll(catalog.stream()
                .map(movie -> buildSnapshot(movie, catalog))
                .toList());
    }

    public List<String> topCandidatesForMovie(String imdbId, int limit) {
        return snapshotRepository.findByImdbId(imdbId)
                .map(snapshot -> snapshot.getCandidateImdbIds().stream()
                        .limit(Math.max(limit, 0))
                        .toList())
                .orElse(List.of());
    }

    private MovieRecommendationSnapshot buildSnapshot(Movie movie, List<Movie> catalog) {
        MovieRecommendationSnapshot snapshot = new MovieRecommendationSnapshot();
        snapshot.setImdbId(movie.getImdbId());
        snapshot.setCandidateImdbIds(catalog.stream()
                .filter(candidate -> !candidate.getImdbId().equals(movie.getImdbId()))
                .sorted((left, right) -> Double.compare(
                        movieSimilarityScorer.contentScore(movie, right),
                        movieSimilarityScorer.contentScore(movie, left)))
                .limit(snapshotSize)
                .map(Movie::getImdbId)
                .toList());
        snapshot.setGeneratedAt(Instant.now());
        return snapshot;
    }
}
