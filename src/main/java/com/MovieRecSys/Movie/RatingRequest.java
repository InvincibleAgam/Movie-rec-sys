package com.MovieRecSys.Movie;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RatingRequest(
        @NotBlank(message = "imdbId is required")
        String imdbId,
        @Min(value = 1, message = "rating must be at least 1")
        @Max(value = 5, message = "rating must be at most 5")
        int rating
) {
}
