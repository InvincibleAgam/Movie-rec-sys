package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AppUser {
    @Id
    private ObjectId id;
    private String displayName;
    private String email;
    private String passwordHash;
    private String authToken;
    private List<String> watchlistImdbIds;
    private Instant createdAt;
}
