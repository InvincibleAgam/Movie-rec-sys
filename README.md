# Movie Atlas

Movie Atlas is a full-stack movie discovery and recommendation platform built with Spring Boot, MongoDB, Redis, RabbitMQ, and a custom frontend. It features a **two-stage recommendation pipeline** (retrieval + ranking) with collaborative filtering, an **event-driven architecture** with dead-letter queue support, **circuit breaker resilience**, and comprehensive **Prometheus/Grafana observability**.

## Live Demo

- Application: [movie-atlas-x0n3.onrender.com](https://movie-atlas-x0n3.onrender.com)
- Health check: [movie-atlas-x0n3.onrender.com/api/v1/health](https://movie-atlas-x0n3.onrender.com/api/v1/health)

Note: the public demo runs on Render's free tier, so the first request after inactivity may take longer while the service wakes up.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full system design, including request paths, async pipeline, cache strategy, consistency model, failure modes, and tradeoffs.

## Highlights

- **Two-stage recommendation engine**: Stage 1 (retrieval) uses content similarity, collaborative filtering, and genre-level popularity. Stage 2 (ranking) scores candidates using 10 features including genre overlap, collaborative signal, recency, and exploration factor.
- **Item-item collaborative filtering** using inverse-user-frequency weighted co-occurrence matrix
- **Offline evaluation pipeline** measuring NDCG@K, Precision@K, Recall@K, and MAP across multiple ranking strategies
- **Event-driven architecture** via RabbitMQ with dead-letter queue, idempotent consumers, and event replay support
- **Circuit breaker resilience** (Resilience4j) around Redis with automatic fallback to database
- **Prometheus/Grafana observability** with custom metrics for recommendation latency (p50/p95/p99), cache hit rates, and event throughput
- **JWT auth with refresh token rotation** and BCrypt-hashed token storage
- **Rate limiting** (Bucket4j) for auth, review, and general API endpoints
- **k6 load testing** suite with configurable scenarios and latency thresholds
- **Event replay system** for rebuilding all materialized state from raw event history

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

## Tech Stack

- Java 21 / Spring Boot 4
- Spring Data MongoDB / MongoDB
- Redis (cache with circuit breaker)
- RabbitMQ (event-driven pipeline)
- Resilience4j (circuit breakers, retry)
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
APP_CACHE_REDIS_ENABLED=true
APP_MESSAGING_RABBITMQ_ENABLED=true
APP_AUTH_JWT_SECRET=your-256-bit-secret
PORT=8080
```

Notes:

- `APP_MESSAGING_RABBITMQ_ENABLED=false` disables RabbitMQ and uses scheduled polling (backward-compatible).
- `APP_CACHE_REDIS_ENABLED=false` disables Redis caching; the app serves directly from MongoDB.
- The bundled movie catalog seeds on startup when the target database is empty.

## API Surface

### Recommendations
- `GET /api/v1/recommendations/movie/{imdbId}` — item-to-item (two-stage pipeline)
- `GET /api/v1/recommendations/for-you` — personalized (authenticated)
- `GET /api/v1/recommendations/profile` — user preference profile
- `GET /api/v1/recommendations/cache/stats` — cache hit/miss stats

### Evaluation
- `GET /api/v1/evaluation/run?k=10` — offline evaluation (NDCG, Precision@K, Recall@K, MAP)
- `POST /api/v1/evaluation/rebuild-collaborative` — rebuild collaborative signals

### Auth
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `DELETE /api/v1/auth/logout`

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

### Admin
- `POST /api/v1/admin/rebuild-collaborative`
- `POST /api/v1/admin/rebuild-snapshots`

### Observability
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
