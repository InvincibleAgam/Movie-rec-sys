package com.MovieRecSys.Movie;

import java.util.List;

public record AuthResponse(
        String token,
        UserProfile user
) {
    public static AuthResponse of(String token, AppUser user) {
        return new AuthResponse(token, UserProfile.from(user));
    }

    public record UserProfile(
            String id,
            String displayName,
            String email,
            List<String> watchlistImdbIds
    ) {
        public static UserProfile from(AppUser user) {
            return new UserProfile(
                    user.getId() != null ? user.getId().toHexString() : null,
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getWatchlistImdbIds()
            );
        }
    }
}
