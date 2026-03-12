package com.MovieRecSys.Movie;

public interface MovieRepositoryCustom {
    MovieCatalogPage searchCatalog(String query, String genre, int page, int size);
}
