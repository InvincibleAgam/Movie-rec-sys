// k6 load testing script for Movie Atlas
// Run: k6 run --vus 50 --duration 30s load-test.js
// Install: brew install k6

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Custom metrics
const recommendationLatency = new Trend('recommendation_latency', true);
const catalogLatency = new Trend('catalog_latency', true);
const errorRate = new Rate('error_rate');

export const options = {
    scenarios: {
        // Scenario 1: Steady catalog browsing
        catalog_browsing: {
            executor: 'constant-vus',
            vus: 20,
            duration: '30s',
            exec: 'browseCatalog',
        },
        // Scenario 2: Recommendation burst
        recommendation_burst: {
            executor: 'ramping-vus',
            startVUs: 5,
            stages: [
                { duration: '10s', target: 50 },
                { duration: '10s', target: 100 },
                { duration: '10s', target: 50 },
            ],
            exec: 'getRecommendations',
        },
        // Scenario 3: Personalized recommendations (authenticated)
        personalized: {
            executor: 'constant-vus',
            vus: 10,
            duration: '30s',
            exec: 'getPersonalized',
        },
    },
    thresholds: {
        'recommendation_latency': ['p(95)<500', 'p(99)<1000'],
        'catalog_latency': ['p(95)<200'],
        'error_rate': ['rate<0.05'],
        'http_req_duration': ['p(50)<200', 'p(95)<500', 'p(99)<1000'],
    },
};

// Pre-test: register a test user
export function setup() {
    const timestamp = Date.now();
    const email = `loadtest-${timestamp}@test.com`;
    const registerRes = http.post(`${BASE_URL}/api/v1/auth/register`, JSON.stringify({
        displayName: 'Load Test User',
        email: email,
        password: 'testpassword123',
    }), { headers: { 'Content-Type': 'application/json' } });

    if (registerRes.status === 200) {
        const body = JSON.parse(registerRes.body);
        return { token: body.token, email: email };
    }

    // If registration fails, try login
    const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
        email: email,
        password: 'testpassword123',
    }), { headers: { 'Content-Type': 'application/json' } });

    if (loginRes.status === 200) {
        const body = JSON.parse(loginRes.body);
        return { token: body.token, email: email };
    }

    return { token: null, email: email };
}

// Scenario: Browse catalog
export function browseCatalog() {
    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/movies`);
    catalogLatency.add(Date.now() - start);

    check(res, {
        'catalog: status 200': (r) => r.status === 200,
        'catalog: has movies': (r) => JSON.parse(r.body).length > 0,
    }) || errorRate.add(1);

    sleep(Math.random() * 2);
}

// Scenario: Get movie recommendations
export function getRecommendations() {
    // First get a list of movies to pick a random one
    const catalogRes = http.get(`${BASE_URL}/api/v1/movies`);
    if (catalogRes.status !== 200) {
        errorRate.add(1);
        return;
    }

    const movies = JSON.parse(catalogRes.body);
    if (!movies.length) return;

    const randomMovie = movies[Math.floor(Math.random() * movies.length)];
    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/recommendations/movie/${randomMovie.imdbId}?limit=6`);
    recommendationLatency.add(Date.now() - start);

    check(res, {
        'recs: status 200': (r) => r.status === 200,
        'recs: has results': (r) => JSON.parse(r.body).length > 0,
    }) || errorRate.add(1);

    sleep(Math.random());
}

// Scenario: Get personalized recommendations (authenticated)
export function getPersonalized(data) {
    if (!data.token) {
        sleep(1);
        return;
    }

    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/recommendations/for-you?limit=6`, {
        headers: { 'Authorization': `Bearer ${data.token}` },
    });
    recommendationLatency.add(Date.now() - start);

    check(res, {
        'personalized: status 200': (r) => r.status === 200,
    }) || errorRate.add(1);

    sleep(Math.random() * 2);
}
