package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AppUserRepository appUserRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public AuthResponse register(AuthRequest request) {
        appUserRepository.findByEmailIgnoreCase(request.email())
                .ifPresent(user -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
                });

        AppUser user = new AppUser();
        user.setDisplayName(request.displayName().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAuthToken(generateToken());
        user.setWatchlistImdbIds(new ArrayList<>());
        user.setCreatedAt(Instant.now());

        AppUser savedUser = appUserRepository.save(user);
        return AuthResponse.of(savedUser.getAuthToken(), savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        user.setAuthToken(generateToken());
        AppUser savedUser = appUserRepository.save(user);
        return AuthResponse.of(savedUser.getAuthToken(), savedUser);
    }

    public AuthResponse.UserProfile me(String authorizationHeader) {
        return AuthResponse.UserProfile.from(requireUser(authorizationHeader));
    }

    public void logout(String authorizationHeader) {
        AppUser user = requireUser(authorizationHeader);
        user.setAuthToken(null);
        appUserRepository.save(user);
    }

    public AppUser requireUser(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }

        return appUserRepository.findByAuthToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token"));
    }

    private String generateToken() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }
}
