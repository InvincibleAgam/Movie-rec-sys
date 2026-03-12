package com.MovieRecSys.Movie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RecommendationServiceTests {
    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final RankingScorer rankingScorer;
    private final ContentSimilarityCandidateGenerator contentGen;
    private final CollaborativeCandidateGenerator collabGen;
    private final PopularInGenreCandidateGenerator popularGen;
    private final UserPreferenceProfileRepository userPreferenceProfileRepository = mock(UserPreferenceProfileRepository.class);
    private final RecommendationProfileProjector recommendationProfileProjector = mock(RecommendationProfileProjector.class);
    private final RecommendationCacheService recommendationCacheService = mock(RecommendationCacheService.class);

    RecommendationServiceTests() {
        MovieSimilarityScorer contentScorer = new MovieSimilarityScorer();
        CollaborativeFilteringService collabService = mock(CollaborativeFilteringService.class);
        RecommendationSnapshotService snapshotService = mock(RecommendationSnapshotService.class);

        this.rankingScorer = new RankingScorer(collabService, contentScorer);
        this.contentGen = new ContentSimilarityCandidateGenerator(snapshotService);
        this.collabGen = new CollaborativeCandidateGenerator(collabService);
        this.popularGen = new PopularInGenreCandidateGenerator(movieRepository);
    }

    private RecommendationService buildService() {
        return new RecommendationService(
                movieRepository,
                authService,
                rankingScorer,
                List.of(contentGen, collabGen, popularGen),
                userPreferenceProfileRepository,
                recommendationProfileProjector,
                recommendationCacheService
        );
    }

    @Test
    void recommendationsReturnResultsForValidMovie() {
        Movie source = movie("tt1", "Interstellar", "Christopher Nolan",
                List.of("Sci-Fi", "Drama"), List.of("space", "survival"), 4.8, 100);
        Movie similar = movie("tt2", "Inception", "Christopher Nolan",
                List.of("Sci-Fi", "Action"), List.of("mind-bending", "space"), 4.7, 95);
        Movie different = movie("tt3", "Whiplash", "Damien Chazelle",
                List.of("Drama", "Music"), List.of("jazz"), 4.8, 75);

        when(recommendationCacheService.movieRecommendationKey("tt1", 2)).thenReturn("movie:tt1:2");
        when(recommendationCacheService.getRecommendationIds("movie:tt1:2")).thenReturn(Optional.empty());
        when(movieRepository.findMovieByImdbId("tt1")).thenReturn(Optional.of(source));
        // Mock searchCatalog to avoid NPE in PopularInGenreCandidateGenerator
        when(movieRepository.searchCatalog(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new MovieCatalogPage(List.of(source, similar, different), 0, 10, 3, 1, false));
        // Mock findByImdbIdIn for candidate hydration in two-stage pipeline
        when(movieRepository.findByImdbIdIn(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(source, similar, different));
        // All generators return empty, so full catalog scan is used
        when(movieRepository.findAll()).thenReturn(List.of(source, similar, different));

        RecommendationService service = buildService();
        List<Movie> recommendations = service.recommendationsForMovie("tt1", 2);

        assertEquals(2, recommendations.size());
        // Source movie should be excluded from results
        recommendations.forEach(movie ->
                org.junit.jupiter.api.Assertions.assertNotEquals("tt1", movie.getImdbId(),
                        "Source movie should not appear in recommendations"));
    }

    private Movie movie(
            String imdbId, String title, String director,
            List<String> genres, List<String> keywords,
            Double averageRating, Integer ratingCount
    ) {
        Movie movie = new Movie();
        movie.setImdbId(imdbId);
        movie.setTitle(title);
        movie.setDirector(director);
        movie.setGenres(genres);
        movie.setKeywords(keywords);
        movie.setAverageRating(averageRating);
        movie.setRatingCount(ratingCount);
        return movie;
    }
}
