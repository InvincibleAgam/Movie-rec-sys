package com.MovieRecSys.Movie;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserExperienceService userExperienceService;

    public UserController(UserExperienceService userExperienceService) {
        this.userExperienceService = userExperienceService;
    }

    @GetMapping("/watchlist")
    public ResponseEntity<WatchlistResponse> watchlist(@RequestHeader("Authorization") String authorizationHeader) {
        return ResponseEntity.ok(userExperienceService.getWatchlist(authorizationHeader));
    }

    @PostMapping("/watchlist/{imdbId}")
    public ResponseEntity<AuthResponse.UserProfile> addToWatchlist(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String imdbId
    ) {
        return ResponseEntity.ok(userExperienceService.addToWatchlist(authorizationHeader, imdbId));
    }

    @DeleteMapping("/watchlist/{imdbId}")
    public ResponseEntity<AuthResponse.UserProfile> removeFromWatchlist(
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable String imdbId
    ) {
        return ResponseEntity.ok(userExperienceService.removeFromWatchlist(authorizationHeader, imdbId));
    }

    @PostMapping("/ratings")
    public ResponseEntity<Rating> rateMovie(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody RatingRequest request
    ) {
        return ResponseEntity.ok(userExperienceService.rateMovie(authorizationHeader, request));
    }

    @GetMapping("/ratings")
    public ResponseEntity<List<Rating>> ratings(@RequestHeader("Authorization") String authorizationHeader) {
        return ResponseEntity.ok(userExperienceService.userRatings(authorizationHeader));
    }
}
