package com.MovieRecSys.Movie;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the request interceptors:
 *  - rate limiting across the whole public API
 *  - an admin-token guard on the operational (admin/evaluation) endpoints
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final RateLimitInterceptor rateLimitInterceptor;
    private final AdminGuardInterceptor adminGuardInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor, AdminGuardInterceptor adminGuardInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.adminGuardInterceptor = adminGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**");
        registry.addInterceptor(adminGuardInterceptor)
                .addPathPatterns("/api/v1/admin/**", "/api/v1/evaluation/**");
    }
}
