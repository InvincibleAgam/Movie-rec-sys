package com.MovieRecSys.Movie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Guards the operational endpoints (/api/v1/admin/** and /api/v1/evaluation/**)
 * which trigger destructive full rebuilds and expensive O(n^2) jobs. Requires a
 * shared secret in the {@code X-Admin-Token} header matching {@code app.admin.token}.
 *
 * Fails closed: if no admin token is configured, all guarded requests are rejected,
 * so the endpoints are never accidentally left open in a deployment.
 */
@Component
public class AdminGuardInterceptor implements HandlerInterceptor {
    private static final String HEADER = "X-Admin-Token";

    private final String adminToken;

    public AdminGuardInterceptor(@Value("${app.admin.token:}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws java.io.IOException {
        String provided = request.getHeader(HEADER);
        if (adminToken == null || adminToken.isBlank() || !constantTimeEquals(adminToken, provided)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Admin authentication required\"}");
            return false;
        }
        return true;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (b == null) {
            return false;
        }
        byte[] ab = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(ab, bb);
    }
}
