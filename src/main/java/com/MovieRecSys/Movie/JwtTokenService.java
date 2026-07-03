package com.MovieRecSys.Movie;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT-based authentication with access + refresh token rotation.
 *
 * Access token:  Short-lived (15 min), stateless, signed with HMAC-SHA256
 * Refresh token: Long-lived (7 days), hashed and stored in MongoDB
 *
 * Security features:
 *   - Refresh tokens are BCrypt-hashed before storage
 *   - Token family-based rotation detects reuse attacks
 *   - Audit log for token operations
 */
@Service
public class JwtTokenService {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final SecretKey signingKey;
    private final long accessTokenValidityMinutes;
    private final long refreshTokenValidityDays;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public JwtTokenService(
            @Value("${app.auth.jwt.secret:default-super-secret-key-that-should-be-changed-in-production-at-least-256-bits}") String secret,
            @Value("${app.auth.jwt.access-token-validity-minutes:15}") long accessTokenValidityMinutes,
            @Value("${app.auth.jwt.refresh-token-validity-days:7}") long refreshTokenValidityDays,
            RefreshTokenRepository refreshTokenRepository
    ) {
        byte[] keyBytes = secret.getBytes();
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityMinutes = accessTokenValidityMinutes;
        this.refreshTokenValidityDays = refreshTokenValidityDays;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Generate an access token for the given user.
     */
    public String generateAccessToken(ObjectId userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toHexString())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenValidityMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a refresh token and persist its hash.
     */
    public String generateRefreshToken(ObjectId userId) {
        String rawToken = UUID.randomUUID().toString(); // 122-bit secret, kept <72B for BCrypt
        String tokenFamily = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(passwordEncoder.encode(rawToken));
        refreshToken.setTokenFamily(tokenFamily);
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenValidityDays, ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        refreshToken.setAuditLog(new ArrayList<>(List.of(
                Instant.now() + " - Token created for user " + userId.toHexString()
        )));

        refreshTokenRepository.save(refreshToken);
        return rawToken + "|" + tokenFamily;
    }

    /**
     * Validate an access token and return the user ID.
     */
    public Optional<String> validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims.getSubject());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Revoke all refresh tokens for a user (logout from all devices).
     */
    public void revokeAllTokens(ObjectId userId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId);
        tokens.forEach(token -> {
            token.setRevoked(true);
            List<String> audit = token.getAuditLog() == null ? new ArrayList<>() : new ArrayList<>(token.getAuditLog());
            audit.add(Instant.now() + " - Token revoked (logout all)");
            token.setAuditLog(audit);
        });
        refreshTokenRepository.saveAll(tokens);
    }

    /** Result of a successful refresh-token rotation. */
    public record RotationResult(ObjectId userId, String newRefreshToken) {}

    /**
     * Rotate a refresh token: validate the presented token, invalidate it, and
     * issue a fresh one within the same token family.
     *
     * Reuse detection: a token whose family is already revoked, or whose raw
     * value no longer matches the stored hash (i.e. an already-rotated token
     * being replayed), triggers revocation of the entire family — the standard
     * refresh-token-reuse defense against stolen tokens.
     *
     * @param refreshTokenValue the client's refresh token, formatted {@code rawToken|tokenFamily}
     * @return the user id and a newly minted refresh token
     * @throws IllegalArgumentException if the token is malformed, expired, unknown, or reused
     */
    public RotationResult rotateRefreshToken(String refreshTokenValue) {
        if (refreshTokenValue == null || !refreshTokenValue.contains("|")) {
            throw new IllegalArgumentException("Malformed refresh token");
        }
        int sep = refreshTokenValue.lastIndexOf('|');
        String rawToken = refreshTokenValue.substring(0, sep);
        String family = refreshTokenValue.substring(sep + 1);

        RefreshToken stored = refreshTokenRepository.findByTokenFamily(family)
                .orElseThrow(() -> new IllegalArgumentException("Unknown refresh token"));

        // Reuse of a family we already retired -> revoke everything for the user.
        if (stored.isRevoked()) {
            revokeAllTokens(stored.getUserId());
            throw new IllegalArgumentException("Refresh token reuse detected");
        }
        if (stored.getExpiresAt() != null && stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            appendAudit(stored, "Refresh token expired");
            refreshTokenRepository.save(stored);
            throw new IllegalArgumentException("Refresh token expired");
        }
        // Raw value no longer matches -> an old (pre-rotation) token is being replayed.
        if (!passwordEncoder.matches(rawToken, stored.getTokenHash())) {
            revokeAllTokens(stored.getUserId());
            throw new IllegalArgumentException("Refresh token reuse detected");
        }

        // Valid -> rotate in place: new raw value, same family, unchanged absolute expiry.
        String newRaw = UUID.randomUUID().toString();
        stored.setTokenHash(passwordEncoder.encode(newRaw));
        appendAudit(stored, "Refresh token rotated");
        refreshTokenRepository.save(stored);

        return new RotationResult(stored.getUserId(), newRaw + "|" + family);
    }

    private void appendAudit(RefreshToken token, String message) {
        List<String> audit = token.getAuditLog() == null ? new ArrayList<>() : new ArrayList<>(token.getAuditLog());
        audit.add(Instant.now() + " - " + message);
        token.setAuditLog(audit);
    }
}
