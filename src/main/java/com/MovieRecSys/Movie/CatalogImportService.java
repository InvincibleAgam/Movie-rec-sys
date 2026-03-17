package com.MovieRecSys.Movie;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class CatalogImportService {
    private final MongoTemplate mongoTemplate;

    public CatalogImportService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public int importCatalogFromClasspath() {
        ClassPathResource resource = new ClassPathResource("data/movie_catalog.csv");
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            int imported = 0;
            for (CSVRecord record : parser) {
                Movie movie = toMovie(record);
                mongoTemplate.upsert(
                        Query.query(Criteria.where("imdbId").is(movie.getImdbId())),
                        buildUpdate(movie),
                        Movie.class
                );
                imported += 1;
            }
            return imported;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to import movie catalog", exception);
        }
    }

    private Movie toMovie(CSVRecord record) {
        Movie movie = new Movie();
        movie.setImdbId(record.get("imdbId"));
        movie.setTitle(record.get("title"));
        movie.setReleaseDate(record.get("releaseDate"));
        movie.setOverview(record.get("overview"));
        movie.setDirector(record.get("director"));
        movie.setCast(splitList(record.get("cast")));
        movie.setKeywords(splitList(record.get("keywords")));
        movie.setRuntimeMinutes(parseInteger(record.get("runtimeMinutes")));
        movie.setTrailerLink(record.get("trailerLink"));
        
        try {
            movie.setStreamLink(record.get("streamLink"));
        } catch (IllegalArgumentException e) {
            // handle gracefully if column is missing from older csv versions
            movie.setStreamLink(null);
        }
        
        movie.setPoster(record.get("poster"));
        movie.setGenres(splitList(record.get("genres")));
        movie.setBackdrops(splitList(record.get("backdrops")));
        movie.setAverageRating(parseDouble(record.get("averageRating")));
        movie.setRatingCount(parseInteger(record.get("ratingCount")));
        return movie;
    }

    private Update buildUpdate(Movie movie) {
        return new Update()
                .set("title", movie.getTitle())
                .set("releaseDate", movie.getReleaseDate())
                .set("overview", movie.getOverview())
                .set("director", movie.getDirector())
                .set("cast", movie.getCast())
                .set("keywords", movie.getKeywords())
                .set("runtimeMinutes", movie.getRuntimeMinutes())
                .set("trailerLink", movie.getTrailerLink())
                .set("streamLink", movie.getStreamLink())
                .set("poster", movie.getPoster())
                .set("genres", movie.getGenres())
                .set("backdrops", movie.getBackdrops())
                .set("averageRating", movie.getAverageRating())
                .set("ratingCount", movie.getRatingCount())
                .setOnInsert("reviews", new ArrayList<>());
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .toList();
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.parseDouble(value);
    }
}
