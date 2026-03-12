package com.MovieRecSys.Movie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

class RecommendationServiceTests {
    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final RatingRepository ratingRepository = mock(RatingRepository.class);
    private final AuthService authService = mock(AuthService.class);
    private final RecommendationService recommendationService =
            new RecommendationService(movieRepository, ratingRepository, authService);

    @Test
    void recommendationsPrioritizeGenreAndDirectorSimilarity() {
        Movie source = movie("tt1", "Interstellar", "Christopher Nolan", List.of("Sci-Fi", "Drama"), List.of("space", "survival"), 4.8, 100);
        Movie similar = movie("tt2", "Inception", "Christopher Nolan", List.of("Sci-Fi", "Action"), List.of("mind-bending", "space"), 4.7, 95);
        Movie different = movie("tt3", "Whiplash", "Damien Chazelle", List.of("Drama", "Music"), List.of("jazz"), 4.8, 75);

        when(movieRepository.findMovieByImdbId("tt1")).thenReturn(java.util.Optional.of(source));
        when(movieRepository.findAll()).thenReturn(List.of(source, similar, different));

        List<Movie> recommendations = recommendationService.recommendationsForMovie("tt1", 2);

        assertEquals("tt2", recommendations.get(0).getImdbId());
        assertEquals("tt3", recommendations.get(1).getImdbId());
    }

    private Movie movie(
            String imdbId,
            String title,
            String director,
            List<String> genres,
            List<String> keywords,
            Double averageRating,
            Integer ratingCount
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
