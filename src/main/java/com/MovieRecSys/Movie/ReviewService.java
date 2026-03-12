package com.MovieRecSys.Movie;

import java.time.Instant;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;


@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final MongoTemplate mongoTemplate;

    public ReviewService(ReviewRepository reviewRepository, MongoTemplate mongoTemplate) {
        this.reviewRepository = reviewRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Review createReview(String reviewBody, String imdbId, String authorName) {
        boolean movieExists = mongoTemplate.exists(
                Query.query(Criteria.where("imdbId").is(imdbId)),
                Movie.class
        );
        if (!movieExists) {
            throw new ResponseStatusException(NOT_FOUND, "Movie not found for imdbId: " + imdbId);
        }

        Review review = reviewRepository.insert(new Review(
                reviewBody,
                authorName == null || authorName.isBlank() ? "Anonymous viewer" : authorName.trim(),
                imdbId,
                Instant.now()
        ));
        mongoTemplate.update(Movie.class)
            .matching(Criteria.where("imdbId").is(imdbId))
            .apply(new Update().push("reviews").value(review))
            .first();
        return review;
    }
}
