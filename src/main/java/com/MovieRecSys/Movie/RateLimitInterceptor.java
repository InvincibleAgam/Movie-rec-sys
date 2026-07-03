package com.MovieRecSys.Movie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces per-client rate limits (Bucket4j) before requests reach controllers.
 *
 * Tiers:
 *   - /api/v1/auth/**            -> auth limit    (brute-force protection for login/register/OTP)
 *   - POST /api/v1/reviews       -> review limit  (spam protection)
 *   - all other /api/v1/**       -> general limit
 *
 * Health is exempt so platform health checks are never throttled. When a bucket
 * is empty the RateLimiter throws 429 TOO_MANY_REQUESTS.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimiter rateLimiter;
    private final boolean enabled;

    public RateLimitInterceptor(
            RateLimiter rateLimiter,
            @Value("${app.rate-limit.enabled:true}") boolean enabled
    ) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (!enabled || path.startsWith("/api/v1/health")) {
            return true;
        }

        String clientId = clientId(request);
        if (path.startsWith("/api/v1/auth/")) {
            rateLimiter.checkAuthLimit(clientId);
        } else if (path.startsWith("/api/v1/reviews") && "POST".equalsIgnoreCase(request.getMethod())) {
            rateLimiter.checkReviewLimit(clientId);
        } else {
            rateLimiter.checkGeneralLimit(clientId);
        }
        return true;
    }

    /**
     * Resolve the client identity for bucketing. Honors the first hop of
     * X-Forwarded-For (the app runs behind Render's proxy) and falls back to the
     * socket address.
     */
    private String clientId(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
