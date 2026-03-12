package com.MovieRecSys.Movie;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Centralized observability metrics for the recommendation system.
 * Exports custom Prometheus metrics for:
 *   - Recommendation endpoint latency (p50/p95/p99)
 *   - Cache hit/miss rates
 *   - Event processing throughput
 *   - Collaborative signal freshness
 */
@Service
public class ObservabilityService {
    private final Timer movieRecommendationTimer;
    private final Timer personalizedRecommendationTimer;
    private final Timer evaluationTimer;
    private final Counter totalRecommendations;
    private final Counter totalPersonalized;
    private final AtomicLong activeCandidateGenerators = new AtomicLong(0);
    private final AtomicLong catalogSize = new AtomicLong(0);

    public ObservabilityService(MeterRegistry meterRegistry) {
        this.movieRecommendationTimer = Timer.builder("recommendations.movie.duration")
                .description("Movie-to-movie recommendation latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.personalizedRecommendationTimer = Timer.builder("recommendations.personalized.duration")
                .description("Personalized recommendation latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.evaluationTimer = Timer.builder("evaluation.duration")
                .description("Offline evaluation run duration")
                .register(meterRegistry);

        this.totalRecommendations = Counter.builder("recommendations.movie.total")
                .description("Total movie-to-movie recommendation requests")
                .register(meterRegistry);

        this.totalPersonalized = Counter.builder("recommendations.personalized.total")
                .description("Total personalized recommendation requests")
                .register(meterRegistry);

        Gauge.builder("system.candidate_generators.active", activeCandidateGenerators, AtomicLong::get)
                .description("Number of active candidate generators")
                .register(meterRegistry);

        Gauge.builder("system.catalog.size", catalogSize, AtomicLong::get)
                .description("Number of movies in catalog")
                .register(meterRegistry);
    }

    public Timer movieRecommendationTimer() { return movieRecommendationTimer; }
    public Timer personalizedRecommendationTimer() { return personalizedRecommendationTimer; }
    public Timer evaluationTimer() { return evaluationTimer; }
    public Counter totalRecommendations() { return totalRecommendations; }
    public Counter totalPersonalized() { return totalPersonalized; }

    public void setCandidateGeneratorCount(long count) { activeCandidateGenerators.set(count); }
    public void setCatalogSize(long size) { catalogSize.set(size); }
}
