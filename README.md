# Movie Atlas

Movie Atlas is a full-stack movie discovery and recommendation platform built with Spring Boot, MongoDB, and a custom static frontend. It supports a seeded movie catalog, personalized recommendations, reviews, ratings, and watchlists.

## Features

- CSV-based catalog import pipeline with idempotent startup seeding
- Larger bundled movie catalog for better demos and testing
- Recommendation engine based on genre, keyword, cast, director, and rating signals
- User registration and login with hashed passwords
- Watchlist and ratings APIs for personalized experiences
- In-browser frontend for catalog browsing, search, recommendations, and account actions
- Docker and Docker Compose setup for local deployment
- GitHub Actions CI workflow for Maven test runs

## Tech stack

- Java 17
- Spring Boot 4
- Spring Data MongoDB
- MongoDB
- Vanilla JavaScript, HTML, and CSS
- Maven

## Run locally

### Option 1: Atlas / external MongoDB

1. Create a `.env` file from `.env.example`
2. Fill in `MONGO_URI` and `MONGO_DATABASE`
3. Run:

```bash
./mvnw spring-boot:run
```

4. Open:

```text
http://localhost:8080
```

### Option 2: Docker Compose with local MongoDB

```bash
docker compose up --build
```

Then open:

```text
http://localhost:8080
```

## AWS deployment

This project is set up to deploy cleanly to AWS App Runner using [apprunner.yaml](/Users/agammanashroy/Desktop/Movie/apprunner.yaml).

### Why AWS App Runner

- Stronger cloud signal for internship resumes than hobby-only platforms
- Minimal ops overhead for a Spring Boot service
- Easy path from GitHub repo to public HTTPS URL

### App Runner deployment steps

1. Push this repo to GitHub
2. In AWS Console, open App Runner
3. Create service from source code repository
4. Connect the GitHub repo
5. Use `apprunner.yaml` for build and run configuration
6. Add environment variables:

```text
MONGO_URI=your-atlas-uri
MONGO_DATABASE=movie-api-db
APP_CATALOG_SEED_ON_STARTUP=true
```

7. Deploy and wait for the public service URL

### Recommended production architecture

- Frontend and backend served from the same Spring Boot app on App Runner
- MongoDB Atlas as the managed database
- GitHub Actions for CI before deploy
- Optional custom domain mapped to App Runner

## API overview

- `GET /api/v1/movies`
- `GET /api/v1/movies/{id}`
- `GET /api/v1/movies/imdb/{imdbId}`
- `POST /api/v1/reviews`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `DELETE /api/v1/auth/logout`
- `GET /api/v1/users/watchlist`
- `POST /api/v1/users/watchlist/{imdbId}`
- `DELETE /api/v1/users/watchlist/{imdbId}`
- `GET /api/v1/users/ratings`
- `POST /api/v1/users/ratings`
- `GET /api/v1/recommendations/movie/{imdbId}`
- `GET /api/v1/recommendations/for-you`
- `GET /api/v1/health`

## Catalog import

The project seeds the bundled CSV catalog from [movie_catalog.csv](/Users/agammanashroy/Desktop/Movie/src/main/resources/data/movie_catalog.csv) when the database is empty. This behavior is controlled by:

```text
APP_CATALOG_SEED_ON_STARTUP=true
```

## Resume angle

This project is strongest on a resume when you highlight both product and engineering depth:

- Built a full-stack movie recommendation platform with Spring Boot, MongoDB, and a responsive frontend
- Designed a catalog ingestion pipeline that seeds and upserts structured movie metadata from CSV
- Implemented personalized recommendation logic using genre, keyword, cast, director, rating, and watchlist signals
- Added authentication, ratings, watchlists, and review flows to support user-specific product behavior
- Containerized the app and added CI-based build verification with GitHub Actions

## Testing

Run:

```bash
./mvnw test
```
