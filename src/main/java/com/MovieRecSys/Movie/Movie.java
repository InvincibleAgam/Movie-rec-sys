package com.MovieRecSys.Movie;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection="movies")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Movie {
    @Id
    private ObjectId id;
    @Indexed(unique = true)
    private String imdbId;
    @Indexed
    private String title;
    private String releaseDate;
    private String overview;
    @Indexed
    private String director;
    private List<String> cast;
    private List<String> keywords;
    private Integer runtimeMinutes;
    private String trailerLink;
    private String poster;
    @Indexed
    private List<String> genres;
    private List<String> backdrops;
    private Double averageRating;
    private Integer ratingCount;
    //The database will only store the ids of the reviews and the reviews themselves will be in separate collection
    //Also known as manual reference relationship over here
    @DocumentReference
    private List<Review> reviews;
}
