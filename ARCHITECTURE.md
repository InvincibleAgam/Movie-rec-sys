# Architecture

This document describes the internal architecture of Movie Atlas, including the request path, async pipeline, caching strategy, consistency model, failure modes, and future work.

## System Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Movie Atlas Application                          │
│                                                                            │
│  ┌──────────┐   ┌──────────────────┐   ┌────────────────────────────────┐  │
│  │ Frontend │──▶│  REST Controllers │──▶│  Two-Stage Recommendation     │  │
│  │ (Vanilla │   │  (Auth, Movies,   │   │  ┌──────────────────────────┐ │  │
│  │  JS/CSS) │   │   Recs, Reviews)  │   │  │ Stage 1: Retrieval      │ │  │
│  └──────────┘   └──────────────────┘   │  │  • Content Similarity    │ │  │
│                                        │  │  • Collaborative Filter  │ │  │
│                                        │  │  • Popular-in-Genre      │ │  │
│                                        │  ├──────────────────────────┤ │  │
│                                        │  │ Stage 2: Ranking         │ │  │
│                                        │  │  10-feature scorer       │ │  │
│                                        │  └──────────────────────────┘ │  │
│                                        └────────────────────────────────┘  │
│                                                                            │
│  ┌──────────────────┐  ┌──────────────┐  ┌────────────────────────────┐   │
│  │ Event Publisher   │─▶│ RabbitMQ      │─▶│ Idempotent Consumer       │   │
│  │ (Dual-write:     │  │ (DLQ support) │  │ (Profile Projector)       │   │
│  │  MongoDB+Queue)  │  └──────────────┘  └────────────────────────────┘   │
│  └──────────────────┘                                                      │
│                                                                            │
│  ┌──────────────────┐  ┌──────────────┐  ┌────────────────────────────┐   │
│  │ Circuit Breaker   │  │ Rate Limiter  │  │ Prometheus Metrics        │   │
│  │ (Redis fallback)  │  │ (Bucket4j)    │  │ (Micrometer)              │   │
│  └──────────────────┘  └──────────────┘  └────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────┘
         │                    │                          │
         ▼                    ▼                          ▼
    ┌─────────┐         ┌─────────┐              ┌───────────┐
    │ MongoDB │         │  Redis  │              │ Prometheus │
    └─────────┘         └─────────┘              │ + Grafana  │
                                                 └───────────┘
```

## Request Path

### Movie-to-Movie Recommendations

1. Client calls `GET /api/v1/recommendations/movie/{imdbId}`
2. `RecommendationService` checks Redis cache via `ResilientCacheService` (circuit-breaker protected)
3. On cache miss:
   - **Stage 1 (Retrieval)**: Three `CandidateGenerator` implementations run in parallel:
     - `ContentSimilarityCandidateGenerator` — O(1) lookup from precomputed snapshots
     - `CollaborativeCandidateGenerator` — co-occurrence neighbors from `CollaborativeSignal`
     - `PopularInGenreCandidateGenerator` — genre-level popularity for exploration
   - Results are unioned and deduplicated (~100 candidates)
   - **Stage 2 (Ranking)**: `RankingScorer` computes a 10-dimensional feature vector per candidate:
     - Genre overlap, keyword overlap, cast overlap, director match
     - Collaborative signal strength
     - Rating affinity, popularity bias, recency boost
     - Exploration factor (stochastic diversity)
     - Preference alignment (user context)
   - Top K candidates by total score are returned
4. Results cached in Redis with TTL

### Personalized Recommendations

Same two-stage pipeline, but with user context:
- Anchors are derived from the user's `UserPreferenceProfile` (built from ratings and watchlist)
- Collaborative signals are aggregated across all anchor movies
- Ranking includes preference alignment score

## Async Pipeline

### Event-Driven Architecture

```
User Action ──▶ InteractionEventPublisher
                    │
                    ├──▶ MongoDB (source of truth, PENDING)
                    │
                    └──▶ RabbitMQ (optional, for real-time processing)
                             │
                             ▼
                    InteractionEventConsumer (idempotent)
                             │
                             ├──▶ Check: already PROCESSED? → skip (idempotency)
                             │
                             ├──▶ Rebuild UserPreferenceProfile
                             │
                             ├──▶ Mark event PROCESSED in MongoDB
                             │
                             └──▶ Invalidate user caches in Redis
                             
                    Failed messages ──▶ Dead Letter Queue (DLQ)
```

### Backward Compatibility

When RabbitMQ is disabled (`app.messaging.rabbitmq.enabled=false`):
- Events persist in MongoDB as PENDING
- `RecommendationProfileProjector` polls every 5 seconds and processes batches
- Same profile rebuild logic, different trigger mechanism

### Event Replay (Technical Spike)

The `EventReplayService` can rebuild all materialized state from scratch:
1. Deletes all `UserPreferenceProfile` documents
2. Resets all `InteractionEvent` status to PENDING
3. Republishes all events to RabbitMQ
4. Idempotent consumers rebuild all profiles

Use case: profile computation logic changes, disaster recovery, or schema migration.

## Caching Strategy

| Data | TTL | Invalidation |
|------|-----|-------------|
| Movie-to-movie recommendations | 15 min | Time-based expiry |
| Personalized recommendations | 3 min | On new user interaction event |
| User preference profile view | 3 min | On new user interaction event |

All cache operations are wrapped in a circuit breaker. When Redis is down:
- Reads return `Optional.empty()` (cache miss)
- Writes are silently dropped
- Application continues serving from MongoDB

## Consistency Model

| Component | Consistency Level |
|-----------|------------------|
| Interaction events | Strongly consistent (MongoDB write-then-publish) |
| User preference profiles | Eventually consistent (async rebuild, seconds delay) |
| Recommendation cache | Eventually consistent (TTL-based + event-driven invalidation) |
| Collaborative signals | Eventually consistent (periodic full rebuild) |
| Content similarity snapshots | Eventually consistent (rebuild on catalog change) |

## Failure Modes

| Failure | Impact | Mitigation |
|---------|--------|-----------|
| Redis down | Higher latency, no cache | Circuit breaker opens, serves from DB |
| RabbitMQ down | Delayed profile updates | Falls back to scheduled polling |
| MongoDB slow | Degraded all operations | Event PENDING backlog, eventual processing |
| Profile stale | Slightly outdated recs | Scheduled projector catches up within seconds |
| Event processing failure | Profile not rebuilt | DLQ captures failed events for retry |
| Collaborative signal stale | Content-only recs | Content-similarity and popularity generators still work |

## Tradeoffs

1. **Precomputation vs. Real-time**: Collaborative signals and content snapshots are precomputed. This trades freshness for latency. A new movie won't appear in collaborative signals until the next rebuild cycle.

2. **Idempotency vs. Performance**: Checking event status before processing adds one DB read per message. This is a deliberate trade: correctness over throughput.

3. **Exploration Factor**: The stochastic diversity injection in rankings means results aren't deterministic. This improves recommendation diversity but makes A/B testing harder.

4. **In-Memory Rate Limiting**: Rate limits reset on application restart. For multi-instance deployments, this should migrate to Redis-backed rate limiting.

## Future Work

- **A/B Testing Framework**: Split users between ranking strategies and measure engagement lift.
- **Online Feature Store**: Move ranking features to a dedicated feature store for real-time serving.
- **Approximate Nearest Neighbor**: Use vector embeddings + ANN index for sub-millisecond retrieval.
- **Multi-Region Read Replicas**: MongoDB read replicas for lower-latency catalog reads.
- **Streaming Feature Computation**: Replace batch co-occurrence rebuild with streaming computation.
