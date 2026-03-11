package com.MovieRecSys.Movie;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MovieService {
    @Autowired
    private MovieRepository movieRepository;
    public List<Movie>  allMovies(){
    return movieRepository.findAll();
    }
    public Optional<Movie> getSingleMovie(ObjectId id) {
        return movieRepository.findById(id);
    }
    public Optional<Movie> getSingleMovie(String imdbId) {
        return movieRepository.findMovieByImdbId(imdbId);
    }
}
