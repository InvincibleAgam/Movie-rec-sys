package com.MovieRecSys.Movie;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import tools.jackson.databind.ObjectMapper;

/**
 * Resilient cache service with circuit breaker protection around Redis operations.
 * When Redis is down, the circuit breaker opens and operations fail fast,
 * allowing the application to serve recommendations from the database
 * without blocking on Redis timeouts.
 */
@Service
@org.springframework.context.annotation.Primary
public class ResilientCacheService extends RecommendationCacheService {
    private static final Logger log = LoggerFactory.getLogger(ResilientCacheService.class);

    private final CircuitBreaker circuitBreaker;
    private final Counter circuitBreakerOpenCounter;
    private final Timer cacheReadTimer;
    private final Timer cacheWriteTimer;

    public ResilientCacheService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${app.cache.redis-enabled:false}") boolean redisEnabled,
            @Value("${app.cache.recommendations.movie-ttl-seconds:900}") long movieTtlSeconds,
            @Value("${app.cache.recommendations.personalized-ttl-seconds:180}") long personalizedTtlSeconds,
            @Value("${app.cache.recommendations.profile-ttl-seconds:180}") long profileTtlSeconds
    ) {
        super(redisTemplateProvider, objectMapper, redisEnabled,
                movieTtlSeconds, personalizedTtlSeconds, profileTtlSeconds);

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        this.circuitBreaker = registry.circuitBreaker("redisCache");

        this.circuitBreakerOpenCounter = Counter.builder("cache.circuit_breaker.open")
                .description("Circuit breaker open events for Redis cache")
                .register(meterRegistry);
        this.cacheReadTimer = Timer.builder("cache.read.duration")
                .description("Cache read latency")
                .register(meterRegistry);
        this.cacheWriteTimer = Timer.builder("cache.write.duration")
                .description("Cache write latency")
                .register(meterRegistry);

        // Register state transition listener
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("Redis circuit breaker state transition: {} -> {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                        circuitBreakerOpenCounter.increment();
                    }
                });
    }

    @Override
    public Optional<List<String>> getRecommendationIds(String key) {
        return cacheReadTimer.record(() -> {
            try {
                return circuitBreaker.executeSupplier(() -> super.getRecommendationIds(key));
            } catch (Exception e) {
                log.debug("Cache read failed (circuit breaker), returning empty: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public void cacheRecommendationIds(String key, List<String> imdbIds, boolean personalized) {
        cacheWriteTimer.record(() -> {
            try {
                circuitBreaker.executeRunnable(() ->
                        super.cacheRecommendationIds(key, imdbIds, personalized));
            } catch (Exception e) {
                log.debug("Cache write failed (circuit breaker): {}", e.getMessage());
            }
        });
    }

    @Override
    public Optional<UserPreferenceProfileView> getProfileView(String key) {
        return cacheReadTimer.record(() -> {
            try {
                return circuitBreaker.executeSupplier(() -> super.getProfileView(key));
            } catch (Exception e) {
                log.debug("Cache read failed (circuit breaker), returning empty: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    @Override
    public void cacheProfileView(String key, UserPreferenceProfileView view) {
        cacheWriteTimer.record(() -> {
            try {
                circuitBreaker.executeRunnable(() -> super.cacheProfileView(key, view));
            } catch (Exception e) {
                log.debug("Cache write failed (circuit breaker): {}", e.getMessage());
            }
        });
    }

    public String circuitBreakerState() {
        return circuitBreaker.getState().name();
    }
}
