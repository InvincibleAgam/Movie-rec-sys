package com.MovieRecSys.Movie;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "ratings")
@CompoundIndex(name = "user_movie_unique_idx", def = "{'userId': 1, 'imdbId': 1}", unique = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Rating {
    @Id
    private ObjectId id;
    private ObjectId userId;
    private String imdbId;
    private int value;
    private Instant updatedAt;
}
