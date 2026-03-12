package com.MovieRecSys.Movie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTests {
    private final AppUserRepository appUserRepository = Mockito.mock(AppUserRepository.class);
    private final AuthService authService = new AuthService(appUserRepository);

    @Test
    void registerCreatesTokenAndNormalizesEmail() {
        when(appUserRepository.findByEmailIgnoreCase("neo@matrix.io")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(new AuthRequest("Neo", "neo@matrix.io", "supersecret"));

        assertNotNull(response.token());
        assertEquals("neo@matrix.io", response.user().email());
        assertEquals("Neo", response.user().displayName());
    }

    @Test
    void requireUserRejectsMissingBearerToken() {
        assertThrows(ResponseStatusException.class, () -> authService.requireUser(null));
    }
}
