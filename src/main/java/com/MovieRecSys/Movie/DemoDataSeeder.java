package com.MovieRecSys.Movie;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Seeds demo interaction data (users + ratings) from a bundled CSV so the
 * recommendation engine has realistic signal on a fresh database.
 *
 * The ratings originate from the public MovieLens ml-latest-small dataset,
 * restricted to the 500 movies in {@code data/movie_catalog.csv}. This gives
 * the collaborative-filtering and offline-evaluation pipelines real
 * human rating behaviour instead of synthetic noise.
 *
 * Runs only when {@code app.demo.seed-ratings.enabled=true} and the ratings
 * collection is empty. Idempotent: a second start is a no-op.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE) // after CatalogBootstrapRunner so the movie catalog exists
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String SEED_FILE = "data/seed_ratings.csv";

    private final AppUserRepository appUserRepository;
    private final RatingRepository ratingRepository;
    private final MovieRepository movieRepository;
    private final CollaborativeFilteringService collaborativeFilteringService;
    private final boolean enabled;

    public DemoDataSeeder(
            AppUserRepository appUserRepository,
            RatingRepository ratingRepository,
            MovieRepository movieRepository,
            CollaborativeFilteringService collaborativeFilteringService,
            @Value("${app.demo.seed-ratings.enabled:false}") boolean enabled
    ) {
        this.appUserRepository = appUserRepository;
        this.ratingRepository = ratingRepository;
        this.movieRepository = movieRepository;
        this.collaborativeFilteringService = collaborativeFilteringService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (ratingRepository.count() > 0) {
            log.info("Demo ratings already present ({}), skipping seed", ratingRepository.count());
            return;
        }
        if (movieRepository.count() == 0) {
            log.warn("No movies in catalog; skipping demo rating seed");
            return;
        }

        List<String[]> rows = readSeedRows();
        if (rows.isEmpty()) {
            log.warn("Seed file {} empty or missing; skipping demo rating seed", SEED_FILE);
            return;
        }

        Instant now = Instant.now();

        // Pass 1: create one AppUser per distinct MovieLens userId and persist,
        // so MongoDB assigns each an ObjectId.
        Map<String, AppUser> usersBySeedId = new LinkedHashMap<>();
        for (String[] row : rows) {
            usersBySeedId.computeIfAbsent(row[0], id -> {
                AppUser u = new AppUser();
                u.setDisplayName("Demo Viewer " + id);
                u.setEmail("demo-viewer-" + id + "@movieatlas.local");
                u.setPasswordHash("$2a$10$demoSeedAccountNotLoginableXXXXXXXXXXXXXXXXXXXXXXXXXX");
                u.setWatchlistImdbIds(new ArrayList<>());
                u.setCreatedAt(now);
                return u;
            });
        }
        appUserRepository.saveAll(usersBySeedId.values());

        // Pass 2: build ratings referencing the now-assigned user ids.
        List<Rating> ratings = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            Rating r = new Rating();
            r.setUserId(usersBySeedId.get(row[0]).getId());
            r.setImdbId(row[1]);
            r.setValue(Integer.parseInt(row[2]));
            r.setUpdatedAt(now);
            ratings.add(r);
        }
        ratingRepository.saveAll(ratings);

        log.info("Seeded {} demo users and {} demo ratings from {}",
                usersBySeedId.size(), ratings.size(), SEED_FILE);

        collaborativeFilteringService.rebuildAll();
        log.info("Collaborative signals rebuilt from seeded ratings");
    }

    private List<String[]> readSeedRows() {
        List<String[]> rows = new ArrayList<>();
        ClassPathResource resource = new ClassPathResource(SEED_FILE);
        if (!resource.exists()) {
            return rows;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    rows.add(new String[] {parts[0].trim(), parts[1].trim(), parts[2].trim()});
                }
            }
        } catch (Exception e) {
            log.warn("Failed reading seed file {}", SEED_FILE, e);
        }
        return rows;
    }
}
