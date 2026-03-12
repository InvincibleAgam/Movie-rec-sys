package com.MovieRecSys.Movie;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/movie/{imdbId}")
    public ResponseEntity<List<Movie>> movieRecommendations(
            @PathVariable String imdbId,
            @RequestParam(defaultValue = "6") int limit
    ) {
        return ResponseEntity.ok(recommendationService.recommendationsForMovie(imdbId, limit));
    }

    @GetMapping("/for-you")
    public ResponseEntity<List<Movie>> forYou(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestParam(defaultValue = "6") int limit
    ) {
        return ResponseEntity.ok(recommendationService.personalizedRecommendations(authorizationHeader, limit));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserPreferenceProfileView> profile(
            @RequestHeader("Authorization") String authorizationHeader
    ) {
        return ResponseEntity.ok(recommendationService.profileView(authorizationHeader));
    }
}
