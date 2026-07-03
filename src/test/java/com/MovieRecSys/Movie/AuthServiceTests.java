package com.MovieRecSys.Movie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTests {
    private final AppUserRepository appUserRepository = Mockito.mock(AppUserRepository.class);
    private final JwtTokenService jwtTokenService = Mockito.mock(JwtTokenService.class);
    private final AuthService authService = new AuthService(appUserRepository, jwtTokenService);

    @Test
    void registerIssuesJwtAndRefreshTokenAndNormalizesEmail() {
        when(appUserRepository.findByEmailIgnoreCase("neo@matrix.io")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(new ObjectId());
            return user;
        });
        when(jwtTokenService.generateAccessToken(any(ObjectId.class), any())).thenReturn("access.jwt.token");
        when(jwtTokenService.generateRefreshToken(any(ObjectId.class))).thenReturn("raw|family");

        AuthResponse response = authService.register(new AuthRequest("Neo", "neo@matrix.io", "supersecret"));

        assertEquals("access.jwt.token", response.token());
        assertEquals("raw|family", response.refreshToken());
        assertNotNull(response.user().id());
        assertEquals("neo@matrix.io", response.user().email());
        assertEquals("Neo", response.user().displayName());
    }

    @Test
    void requireUserRejectsMissingBearerToken() {
        assertThrows(ResponseStatusException.class, () -> authService.requireUser(null));
    }

    @Test
    void requireUserRejectsInvalidJwt() {
        when(jwtTokenService.validateAccessToken("bad-token")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> authService.requireUser("Bearer bad-token"));
    }

    @Test
    void refreshRotationLoadsUserForRotatedToken() {
        ObjectId userId = new ObjectId();
        AppUser user = new AppUser();
        user.setId(userId);
        user.setEmail("neo@matrix.io");
        when(jwtTokenService.rotateRefreshToken("raw|family"))
                .thenReturn(new JwtTokenService.RotationResult(userId, "newraw|family"));
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenService.generateAccessToken(userId, "neo@matrix.io")).thenReturn("new.access.jwt");

        AuthResponse response = authService.refresh("raw|family");

        assertEquals("new.access.jwt", response.token());
        assertEquals("newraw|family", response.refreshToken());
    }

    @Test
    void refreshRejectsReusedToken() {
        when(jwtTokenService.rotateRefreshToken("stolen|family"))
                .thenThrow(new IllegalArgumentException("Refresh token reuse detected"));
        assertThrows(ResponseStatusException.class, () -> authService.refresh("stolen|family"));
    }
}
