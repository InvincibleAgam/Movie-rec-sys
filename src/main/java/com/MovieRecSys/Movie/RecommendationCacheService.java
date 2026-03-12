package com.MovieRecSys.Movie;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class RecommendationCacheService {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectMapper objectMapper;
    private final boolean redisEnabled;
    private final Duration movieTtl;
    private final Duration personalizedTtl;
    private final Duration profileTtl;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong writes = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();

    public RecommendationCacheService(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectMapper objectMapper,
            @Value("${app.cache.redis-enabled:false}") boolean redisEnabled,
            @Value("${app.cache.recommendations.movie-ttl-seconds:900}") long movieTtlSeconds,
            @Value("${app.cache.recommendations.personalized-ttl-seconds:180}") long personalizedTtlSeconds,
            @Value("${app.cache.recommendations.profile-ttl-seconds:180}") long profileTtlSeconds
    ) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.objectMapper = objectMapper;
        this.redisEnabled = redisEnabled;
        this.movieTtl = Duration.ofSeconds(movieTtlSeconds);
        this.personalizedTtl = Duration.ofSeconds(personalizedTtlSeconds);
        this.profileTtl = Duration.ofSeconds(profileTtlSeconds);
    }

    public Optional<List<String>> getRecommendationIds(String key) {
        return readValue(key, STRING_LIST_TYPE);
    }

    public void cacheRecommendationIds(String key, List<String> imdbIds, boolean personalized) {
        writeValue(key, imdbIds, personalized ? personalizedTtl : movieTtl);
    }

    public Optional<UserPreferenceProfileView> getProfileView(String key) {
        return readValue(key, UserPreferenceProfileView.class);
    }

    public void cacheProfileView(String key, UserPreferenceProfileView view) {
        writeValue(key, view, profileTtl);
    }

    public void invalidateUserCaches(ObjectId userId) {
        if (!redisAvailable()) {
            return;
        }

        String userToken = userId.toHexString();
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getObject();
            redisTemplate.delete(redisTemplate.keys("recommendations:user:" + userToken + ":limit:*"));
            redisTemplate.delete(profileViewKey(userId));
            invalidations.incrementAndGet();
        } catch (RuntimeException ignored) {
        }
    }

    public RecommendationCacheStatsView statsView() {
        return new RecommendationCacheStatsView(
                redisEnabled && redisTemplateProvider.getIfAvailable() != null,
                hits.get(),
                misses.get(),
                writes.get(),
                invalidations.get()
        );
    }

    public String movieRecommendationKey(String imdbId, int limit) {
        return "recommendations:movie:" + imdbId + ":limit:" + limit;
    }

    public String personalizedRecommendationKey(ObjectId userId, int limit) {
        return "recommendations:user:" + userId.toHexString() + ":limit:" + limit;
    }

    public String profileViewKey(ObjectId userId) {
        return "recommendations:profile:" + userId.toHexString();
    }

    private <T> Optional<T> readValue(String key, Class<T> valueType) {
        if (!redisAvailable()) {
            misses.incrementAndGet();
            return Optional.empty();
        }

        try {
            String raw = redisTemplateProvider.getObject().opsForValue().get(key);
            if (raw == null) {
                misses.incrementAndGet();
                return Optional.empty();
            }
            hits.incrementAndGet();
            return Optional.of(objectMapper.readValue(raw, valueType));
        } catch (Exception ignored) {
            misses.incrementAndGet();
            return Optional.empty();
        }
    }

    private <T> Optional<T> readValue(String key, TypeReference<T> valueType) {
        if (!redisAvailable()) {
            misses.incrementAndGet();
            return Optional.empty();
        }

        try {
            String raw = redisTemplateProvider.getObject().opsForValue().get(key);
            if (raw == null) {
                misses.incrementAndGet();
                return Optional.empty();
            }
            hits.incrementAndGet();
            return Optional.of(objectMapper.readValue(raw, valueType));
        } catch (Exception ignored) {
            misses.incrementAndGet();
            return Optional.empty();
        }
    }

    private void writeValue(String key, Object value, Duration ttl) {
        if (!redisAvailable()) {
            return;
        }

        try {
            redisTemplateProvider.getObject().opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            writes.incrementAndGet();
        } catch (Exception ignored) {
        }
    }

    private boolean redisAvailable() {
        return redisEnabled && redisTemplateProvider.getIfAvailable() != null;
    }
}
