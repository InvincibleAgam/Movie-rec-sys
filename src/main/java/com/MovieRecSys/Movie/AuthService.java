package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authentication using stateless JWT access tokens plus rotating refresh tokens.
 *
 * On register/login the client receives:
 *   - {@code token}        : a short-lived (15 min) signed JWT access token
 *   - {@code refreshToken} : a long-lived (7 day) opaque token, stored only as a
 *                            BCrypt hash, rotated on every use with reuse detection
 *
 * Protected endpoints authenticate by validating the JWT signature + expiry and
 * loading the user referenced by the token subject — no per-request DB token
 * lookup, and no long-lived bearer token sitting in the database.
 */
@Service
public class AuthService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AppUserRepository appUserRepository;
    private final JwtTokenService jwtTokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository appUserRepository, JwtTokenService jwtTokenService) {
        this.appUserRepository = appUserRepository;
        this.jwtTokenService = jwtTokenService;
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
        user.setWatchlistImdbIds(new ArrayList<>());
        user.setCreatedAt(Instant.now());

        AppUser savedUser = appUserRepository.save(user);
        return issueTokens(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        return issueTokens(user);
    }

    /**
     * Exchange a valid refresh token for a new access token and a rotated refresh
     * token. Detects and defends against refresh-token reuse.
     */
    public AuthResponse refresh(String refreshTokenValue) {
        JwtTokenService.RotationResult rotation;
        try {
            rotation = jwtTokenService.rotateRefreshToken(refreshTokenValue);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }

        AppUser user = appUserRepository.findById(rotation.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));

        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail());
        return AuthResponse.of(accessToken, rotation.newRefreshToken(), user);
    }

    public AuthResponse.UserProfile me(String authorizationHeader) {
        return AuthResponse.UserProfile.from(requireUser(authorizationHeader));
    }

    /**
     * Log out by revoking every refresh token for the user. The current access
     * token remains valid until it expires (≤15 min) — the standard trade-off for
     * stateless JWTs.
     */
    public void logout(String authorizationHeader) {
        AppUser user = requireUser(authorizationHeader);
        jwtTokenService.revokeAllTokens(user.getId());
    }

    public AppUser requireUser(String authorizationHeader) {
        return findUserByAuthorizationHeader(authorizationHeader)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session token"));
    }

    public Optional<AppUser> findUserByAuthorizationHeader(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            return Optional.empty();
        }

        return jwtTokenService.validateAccessToken(token)
                .flatMap(this::toObjectId)
                .flatMap(appUserRepository::findById);
    }

    private AuthResponse issueTokens(AppUser user) {
        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenService.generateRefreshToken(user.getId());
        return AuthResponse.of(accessToken, refreshToken, user);
    }

    private Optional<ObjectId> toObjectId(String hex) {
        return ObjectId.isValid(hex) ? Optional.of(new ObjectId(hex)) : Optional.empty();
    }
}
