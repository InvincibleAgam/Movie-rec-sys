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

- Java 21
- Spring Boot 4
- Spring Data MongoDB
- MongoDB
- Vanilla JavaScript, HTML, and CSS
- Maven

## Run locally

### Option 1: Atlas / external MongoDB

1. Create a `.env` file from `.env.example`
2. Paste your MongoDB Atlas connection string into `MONGO_URI` and set `MONGO_DATABASE`
3. Keep `.env` local only and never commit real connection strings
4. Run:

```bash
./mvnw spring-boot:run
```

5. Open:

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
5. Use [apprunner.yaml](/Users/agammanashroy/Desktop/Movie/apprunner.yaml) for build and run configuration
6. Add environment variables:

```text
MONGO_URI=your-atlas-uri
MONGO_DATABASE=movie-api-db
APP_CATALOG_SEED_ON_STARTUP=true
APP_CACHE_REDIS_ENABLED=false
```

7. In Health Check settings, use path `/api/v1/health`
8. Deploy and wait for the public service URL
9. Verify the public URL with:

```bash
curl https://your-app-url/api/v1/health
```

### Why these settings matter

- App Runner injects `PORT`, and the app now honors it via `server.port=${PORT:8080}`
- MongoDB Atlas is required because App Runner cannot reach your local Docker MongoDB
- Redis caching should stay disabled on App Runner unless you add a managed Redis instance

### App Runner smoke test

After deployment, confirm the public service works end to end:

```bash
APP_URL="https://your-app-url"

curl -s "$APP_URL/api/v1/health"

EMAIL="demo$(date +%s)@example.com"

TOKEN=$(
  curl -s -X POST "$APP_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"displayName\":\"Demo User\",\"email\":\"$EMAIL\",\"password\":\"supersecret\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])'
)

curl -s -H "Authorization: Bearer $TOKEN" "$APP_URL/api/v1/auth/me"
```

## Free deployment on Render

This repo also includes a free Render Blueprint in [render.yaml](/Users/agammanashroy/Desktop/Movie/render.yaml), which is the easiest zero-cost way to get a public demo URL.

### Render deployment steps

1. Create a Render account and connect your GitHub account.
2. In the Render dashboard, click `New` and choose `Blueprint`.
3. Select this repository and the `main` branch.
4. Render will detect [render.yaml](/Users/agammanashroy/Desktop/Movie/render.yaml).
5. When prompted for environment variables, set:

```text
MONGO_URI=your-mongodb-atlas-uri
```

6. Keep the default values from the Blueprint for:

```text
MONGO_DATABASE=movie-api-db
APP_CATALOG_SEED_ON_STARTUP=true
APP_CACHE_REDIS_ENABLED=false
```

7. In MongoDB Atlas, allow inbound access from Render.
For the simplest setup, add `0.0.0.0/0` in Atlas Network Access, then tighten it later if needed.
8. Create the Blueprint and wait for the service to finish deploying.
9. Open the generated `onrender.com` URL and verify:

```bash
APP_URL="https://your-render-url.onrender.com"
curl -s "$APP_URL/api/v1/health"
```

### Render free-tier notes

- Free Render web services spin down after 15 minutes without traffic and can take about a minute to wake up again.
- This is fine for demos and resume links, but the first request after idle will be slow.
- The app uses MongoDB Atlas for persistence, so your data survives Render restarts and free-tier cold starts.

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
