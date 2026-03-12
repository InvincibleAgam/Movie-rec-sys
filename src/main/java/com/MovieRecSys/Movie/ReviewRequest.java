package com.MovieRecSys.Movie;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(
        @NotBlank(message = "reviewBody is required")
        String reviewBody,
        @NotBlank(message = "imdbId is required")
        String imdbId,
        String authorName
) {
}
