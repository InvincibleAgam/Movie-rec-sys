package com.MovieRecSys.Movie;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class MovieRepositoryImpl implements MovieRepositoryCustom {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final MongoTemplate mongoTemplate;

    public MovieRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public MovieCatalogPage searchCatalog(String query, String genre, int page, int size) {
        int sanitizedPage = Math.max(page, 0);
        int sanitizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Query baseQuery = buildQuery(query, genre);

        long totalItems = mongoTemplate.count(baseQuery, Movie.class);
        Query pagedQuery = buildQuery(query, genre)
                .with(Sort.by(
                        Sort.Order.desc("averageRating"),
                        Sort.Order.desc("ratingCount"),
                        Sort.Order.asc("title")))
                .skip((long) sanitizedPage * sanitizedSize)
                .limit(totalItems == 0 ? DEFAULT_PAGE_SIZE : sanitizedSize);

        List<Movie> items = mongoTemplate.find(pagedQuery, Movie.class);
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / sanitizedSize);

        return new MovieCatalogPage(
                items,
                sanitizedPage,
                sanitizedSize,
                totalItems,
                totalPages,
                sanitizedPage + 1 < totalPages
        );
    }

    private Query buildQuery(String query, String genre) {
        List<Criteria> filters = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String escaped = Pattern.quote(query.trim());
            filters.add(new Criteria().orOperator(
                    Criteria.where("title").regex(escaped, "i"),
                    Criteria.where("director").regex(escaped, "i"),
                    Criteria.where("genres").regex(escaped, "i"),
                    Criteria.where("keywords").regex(escaped, "i")
            ));
        }
        if (genre != null && !genre.isBlank()) {
            filters.add(Criteria.where("genres").is(genre.trim()));
        }

        Query queryObject = new Query();
        if (filters.size() == 1) {
            queryObject.addCriteria(filters.get(0));
        } else if (!filters.isEmpty()) {
            queryObject.addCriteria(new Criteria().andOperator(filters.toArray(Criteria[]::new)));
        }
        return queryObject;
    }
}
