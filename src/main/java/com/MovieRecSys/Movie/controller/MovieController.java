package com.MovieRecSys.Movie.controller;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.MovieRecSys.Movie.Movie;
import com.MovieRecSys.Movie.MovieCatalogPage;
import com.MovieRecSys.Movie.MovieService;

@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {
    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public ResponseEntity<List<Movie>> getallMovies() {
        return new ResponseEntity<>(movieService.allMovies(), HttpStatus.OK);
    }

    @GetMapping("/catalog")
    public ResponseEntity<MovieCatalogPage> catalog(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(movieService.catalog(q, genre, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Movie> getSingleMovie(@PathVariable ObjectId id) {
        Movie movie = movieService.getSingleMovie(id);
        if (movie == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(movie);
    }

    @GetMapping("/imdb/{imdbId}")
    public ResponseEntity<Movie> getSingleMovie(@PathVariable String imdbId) {
        Movie movie = movieService.getSingleMovie(imdbId);
        if (movie == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(movie);
    }
}
