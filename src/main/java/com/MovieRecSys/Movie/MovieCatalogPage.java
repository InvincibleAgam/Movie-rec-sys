package com.MovieRecSys.Movie;

import java.util.List;

public record MovieCatalogPage(
        List<Movie> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext
) {
}
