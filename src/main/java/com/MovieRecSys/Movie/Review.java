package com.MovieRecSys.Movie;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "reviews")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Review {
    @Id
    private ObjectId id;
    private String body;
    private String authorName;
    private String imdbId;
    private Instant createdAt;

    public Review(String body) {
        this.body = body;
    }

    public Review(String body, String authorName, String imdbId, Instant createdAt) {
        this.body = body;
        this.authorName = authorName;
        this.imdbId = imdbId;
        this.createdAt = createdAt;
    }
}
