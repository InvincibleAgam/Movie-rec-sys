package com.MovieRecSys.Movie;

import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Comparator;

@Service
public class MovieService {
    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public List<Movie> allMovies() {
        return movieRepository.findAll().stream()
                .sorted(Comparator.comparing(Movie::getAverageRating, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Movie::getRatingCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Movie::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public MovieCatalogPage catalog(String query, String genre, Integer page, Integer size) {
        return movieRepository.searchCatalog(query, genre, page == null ? 0 : page, size == null ? 20 : size);
    }

    public Movie getSingleMovie(ObjectId id) {
        return movieRepository.findById(id).orElse(null);
    }

    public Movie getSingleMovie(String imdbId) {
        return movieRepository.findMovieByImdbId(imdbId).orElse(null);
    }
}
