package com.MovieRecSys.Movie;

import java.util.List;

public record WatchlistResponse(
        List<Movie> movies
) {
}
