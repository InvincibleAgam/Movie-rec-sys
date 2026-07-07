# Movie Atlas

Movie Atlas is a full-stack movie discovery and recommendation platform built with Spring Boot, MongoDB, Redis, RabbitMQ, and a custom frontend. It features a **two-stage recommendation pipeline** (retrieval + ranking) with item-item collaborative filtering, a **leakage-free offline evaluation harness**, **JWT authentication with rotating refresh tokens**, **Redis caching** (measured ~91–98% latency reduction), and **Prometheus/Grafana observability**.

## Live Demo

- Application: [movie-atlas-x0n3.onrender.com](https://movie-atlas-x0n3.onrender.com)
- Health check: [movie-atlas-x0n3.onrender.com/api/v1/health](https://movie-atlas-x0n3.onrender.com/api/v1/health)

Note: the public demo runs on Render's free tier, so the first request after inactivity may take longer while the service wakes up.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full system design, including request paths, async pipeline, cache strategy, consistency model, failure modes, and tradeoffs.

## Highlights

- **Two-stage recommendation engine**: Stage 1 (retrieval) uses content similarity, collaborative filtering, and genre-level popularity. Stage 2 (ranking) scores candidates using 10 features including genre overlap, collaborative signal, recency, and exploration factor.
- **Item-item collaborative filtering** using an inverse-user-frequency weighted co-occurrence matrix
- **Leakage-free offline evaluation** measuring NDCG@K, Precision@K, Recall@K, and MAP across ranking strategies, with the collaborative matrix rebuilt from the training split only and exploration disabled, so results are reproducible (see [Performance](#performance-measured))
- **Real-time personalization**: rating or watchlisting a movie synchronously rebuilds the user's preference profile and evicts their cached recommendations, so "For you" updates immediately
- **Stateless JWT authentication** (HS512 access tokens, 15-minute TTL) with **rotating refresh tokens** — stored BCrypt-hashed at rest, rotated on every use, with token-family **reuse detection** that revokes a compromised family on replay
- **Rate limiting** (Bucket4j token buckets) enforced per client IP via a servlet interceptor: auth (10/min), review (5/min), and general (60/min) endpoints
- **Protected operational endpoints**: the admin and evaluation endpoints (destructive rebuilds / expensive jobs) require an `X-Admin-Token`, failing closed when no token is configured
- **Meaningful health check**: `/api/v1/health` pings MongoDB and returns `503` when the database is unreachable (instead of a hardcoded "UP")
- **Redis caching** that cuts recommendation API latency by ~91% (item-item) to ~98% (personalized) at a ~91% cache-hit rate; Redis failures are caught and degrade gracefully to a MongoDB recompute
- **Real-world data**: a 500-movie catalog with real posters, cast, directors and trailers (enriched from [TMDB](https://www.themoviedb.org/)) plus ~5,000 ratings across ~110 users sourced from the [MovieLens](https://grouplens.org/datasets/movielens/) dataset, so collaborative filtering and offline evaluation run on genuine user behaviour
- **Event-driven pipeline** via RabbitMQ (optional — the default uses a scheduled projector) with idempotent consumers and a dead-letter-queue topology
- **Prometheus/Grafana observability** with custom metrics for recommendation latency (p50/p95/p99), cache hit rates, and event throughput
- **k6 load testing** suite with configurable scenarios and latency thresholds

## Core Features

- User registration, login, logout, and profile lookup (JWT + refresh tokens)
- Movie catalog browsing, detail views, and search
- Ratings, reviews, and watchlist management
- Item-to-item recommendation API (two-stage pipeline)
- Personalized "for you" recommendations (collaborative + content hybrid)
- Offline evaluation metrics endpoint
- Materialized recommendation profile inspection
- Admin endpoints for signal rebuilds
- Health and Prometheus metrics endpoints

## Performance (measured)

Measured locally on the bundled dataset (500 movies, ~5,000 ratings, ~110 users) with MongoDB + Redis in Docker.

### Redis cache — recommendation API latency

Sequential client-side latency (steady state, warm cache), Redis disabled vs. enabled:

| Endpoint | p50 (cache off) | p50 (cache on) | p95 (off → on) | Reduction |
| --- | --- | --- | --- | --- |
| Item-item `/recommendations/movie/{id}` | 71.6 ms | 6.2 ms | 104.2 → 7.6 ms | **~91%** |
| Personalized `/recommendations/for-you` | 403.4 ms | 6.8 ms | 507.5 → 7.8 ms | **~98%** |

Cache-hit rate over the run: **~91%**. The personalized endpoint benefits most because its two-stage pipeline recomputes per-candidate collaborative lookups on every uncached request. Reproduce by toggling `APP_CACHE_REDIS_ENABLED` and hitting the endpoints (set `APP_RATE_LIMIT_ENABLED=false` first so the load isn't throttled).

### Offline recommendation quality

Offline evaluation via `GET /api/v1/evaluation/run?k=10` — per-user 70/30 train/test split, relevance = rating ≥ 4, averaged over 113 users:

| Strategy | NDCG@10 | Precision@10 | Recall@10 | MAP |
| --- | --- | --- | --- | --- |
| Content-only | 0.034 | 0.028 | 0.033 | 0.011 |
| Content + engagement | 0.044 | 0.042 | 0.042 | 0.014 |
| Full pipeline (content + collaborative + ranking) | **0.079** | **0.057** | **0.061** | **0.037** |

The full two-stage pipeline outperforms content-only by **~2.3×** on NDCG@10, confirming that item-item collaborative filtering adds real ranking signal on this dataset.

**Leakage-free & deterministic methodology:** the collaborative co-occurrence matrix used during evaluation is rebuilt **only from the training split** (never the held-out test ratings), and the ranker's stochastic exploration term is disabled for evaluation. As a result the metrics are reproducible across runs. (An earlier version built the collaborative signals over the full rating set, which leaked test data into the collaborative feature and inflated the full-pipeline NDCG to ~0.29 — the numbers above are the corrected, leakage-free figures.)

## Tech Stack

- Java 21 / Spring Boot 4
- Spring Data MongoDB / MongoDB (with auto-created indexes)
- Redis (recommendation cache with graceful MongoDB fallback)
- RabbitMQ (optional event-driven pipeline)
- Resilience4j (cache resilience wrapper)
- Micrometer + Prometheus + Grafana (observability)
- JJWT (JWT authentication)
- Bucket4j (rate limiting)
- k6 (load testing)
- Vanilla JavaScript, HTML, CSS (frontend)
- Maven / Docker / Docker Compose

## Project Structure

- Backend application: [`src/main/java`](src/main/java)
- Static frontend assets: [`src/main/resources/static`](src/main/resources/static)
- Seed catalog: [`src/main/resources/data/movie_catalog.csv`](src/main/resources/data/movie_catalog.csv)
- Architecture document: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- Monitoring config: [`monitoring/`](monitoring/)
- Load testing: [`load-test.js`](load-test.js)
- Docker Compose: [`docker-compose.yml`](docker-compose.yml)
- Render deployment: [`render.yaml`](render.yaml)
- AWS App Runner config: [`apprunner.yaml`](apprunner.yaml)

## Local Development

### Option 1: Run against MongoDB Atlas

1. Create a local `.env` file from [`.env.example`](.env.example).
2. Set `MONGO_URI` to your MongoDB Atlas connection string.
3. Set `MONGO_DATABASE=movie-api-db`.
4. Start the application:

```bash
./mvnw spring-boot:run
```

5. Open [http://localhost:8080](http://localhost:8080).

### Option 2: Run with Docker Compose (full stack)

```bash
docker compose up --build
```

This starts the Spring Boot app, MongoDB, Redis, RabbitMQ (with management UI at port 15672), Prometheus, and Grafana (at port 3000).

## Configuration

Common environment variables:

```text
MONGO_URI=your-mongodb-uri
MONGO_DATABASE=movie-api-db
APP_CATALOG_SEED_ON_STARTUP=true
APP_DEMO_SEED_RATINGS_ENABLED=false
APP_CACHE_REDIS_ENABLED=true
APP_MESSAGING_RABBITMQ_ENABLED=true
APP_RATE_LIMIT_ENABLED=true
APP_AUTH_JWT_SECRET=your-256-bit-secret
APP_ADMIN_TOKEN=your-admin-token
PORT=8080
```

Notes:

- `APP_MESSAGING_RABBITMQ_ENABLED=false` disables RabbitMQ and uses scheduled polling (backward-compatible).
- `APP_CACHE_REDIS_ENABLED=false` disables Redis caching; the app serves directly from MongoDB.
- `APP_RATE_LIMIT_ENABLED=false` turns off per-IP rate limiting (useful for load testing).
- The bundled movie catalog seeds on startup when the target database is empty.
- `APP_AUTH_JWT_SECRET` should be set to a strong 256-bit secret in any real deployment; the default is for local use only.
- `APP_ADMIN_TOKEN` must be set to call the admin/evaluation endpoints; when blank, those endpoints are locked (fail closed).

### Dataset & demo seeding

The bundled catalog ([`data/movie_catalog.csv`](src/main/resources/data/movie_catalog.csv)) holds **500 real movies** — real titles, IMDb IDs and genres from the [MovieLens](https://grouplens.org/datasets/movielens/) dataset, enriched with **posters, cast, directors, runtimes and trailer links from [TMDB](https://www.themoviedb.org/)**. To also populate realistic interaction data, set `APP_DEMO_SEED_RATINGS_ENABLED=true`: on a fresh database the app seeds **~5,000 real ratings across ~110 users** from [`data/seed_ratings.csv`](src/main/resources/data/seed_ratings.csv) and rebuilds the collaborative-filtering signals — giving the recommender and the offline evaluation genuine behavioural data to work with. The seeder is idempotent and only runs when the ratings collection is empty. (Poster/metadata URLs are baked into the catalog CSV; no TMDB API key is required to run the app.)

## API Surface

### Recommendations
- `GET /api/v1/recommendations/movie/{imdbId}` — item-to-item (two-stage pipeline)
- `GET /api/v1/recommendations/for-you` — personalized (authenticated)
- `GET /api/v1/recommendations/profile` — user preference profile
- `GET /api/v1/recommendations/cache/stats` — cache hit/miss stats

### Evaluation (requires `X-Admin-Token` header)
- `GET /api/v1/evaluation/run?k=10` — offline evaluation (NDCG, Precision@K, Recall@K, MAP)
- `POST /api/v1/evaluation/rebuild-collaborative` — rebuild collaborative signals

### Auth
- `POST /api/v1/auth/register` — returns a JWT access token + a refresh token
- `POST /api/v1/auth/login` — returns a JWT access token + a refresh token
- `POST /api/v1/auth/refresh` — exchange a refresh token for a new access token (rotates the refresh token)
- `GET /api/v1/auth/me` — current user (requires `Authorization: Bearer <accessToken>`)
- `DELETE /api/v1/auth/logout` — revokes the user's refresh tokens

### Movies & Interactions
- `GET /api/v1/movies`
- `GET /api/v1/movies/{id}`
- `GET /api/v1/movies/imdb/{imdbId}`
- `GET /api/v1/movies/catalog`
- `POST /api/v1/reviews`
- `GET /api/v1/users/watchlist`
- `POST /api/v1/users/watchlist/{imdbId}`
- `DELETE /api/v1/users/watchlist/{imdbId}`
- `GET /api/v1/users/ratings`
- `POST /api/v1/users/ratings`

### Admin (requires `X-Admin-Token` header)
- `POST /api/v1/admin/rebuild-collaborative`
- `POST /api/v1/admin/rebuild-snapshots`

### Health & Observability
- `GET /api/v1/health` — pings MongoDB; returns `503` if the database is unreachable
- `GET /actuator/health`
- `GET /actuator/prometheus`
- `GET /actuator/metrics`

## Load Testing

```bash
# Install k6
brew install k6

# Run with 50 virtual users for 30 seconds
k6 run --vus 50 --duration 30s load-test.js

# Or run the full scenario suite
k6 run load-test.js
```

## Testing

```bash
./mvnw test
```

## Monitoring

When running with Docker Compose:

- **Prometheus**: [http://localhost:9090](http://localhost:9090)
- **Grafana**: [http://localhost:3000](http://localhost:3000) (admin/admin)
- **RabbitMQ Management**: [http://localhost:15672](http://localhost:15672) (guest/guest)

## Security Notes

- Real credentials must never be committed to the repository.
- Keep `.env` files local and rotate any credential that is accidentally exposed.
- For public deployments, store connection strings, JWT secrets, and credentials in the hosting platform's environment-variable manager.
- The JWT secret must be at least 256 bits for HMAC-SHA256 signing.

## License

This repository currently does not include a separate license file. Add one before accepting external contributions or reusing the project commercially.
