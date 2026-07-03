package com.MovieRecSys.Movie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CatalogBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CatalogBootstrapRunner.class);

    private final CatalogImportService catalogImportService;
    private final MongoTemplate mongoTemplate;
    private final MovieRepository movieRepository;
    private final RecommendationSnapshotService recommendationSnapshotService;
    private final boolean seedOnStartup;

    public CatalogBootstrapRunner(
            CatalogImportService catalogImportService,
            MongoTemplate mongoTemplate,
            MovieRepository movieRepository,
            RecommendationSnapshotService recommendationSnapshotService,
            @Value("${app.catalog.seed-on-startup:true}") boolean seedOnStartup
    ) {
        this.catalogImportService = catalogImportService;
        this.mongoTemplate = mongoTemplate;
        this.movieRepository = movieRepository;
        this.recommendationSnapshotService = recommendationSnapshotService;
        this.seedOnStartup = seedOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedOnStartup) {
            return;
        }
        // Run seeding off the startup thread so a slow/large catalog import or the
        // O(n^2) snapshot rebuild can never block port binding, fail the platform
        // health check, or crash the application on boot. Errors are logged, not fatal.
        Thread worker = new Thread(this::seedCatalog, "catalog-bootstrap");
        worker.setDaemon(true);
        worker.start();
    }

    private void seedCatalog() {
        try {
            catalogImportService.importCatalogFromClasspath();

            // Backfill missing stream links for legacy movies not in the CSV
            Query query = Query.query(Criteria.where("streamLink").exists(false));
            for (Movie m : mongoTemplate.find(query, Movie.class)) {
                if (m.getTitle() != null) {
                    String safeTitle = URLEncoder.encode(m.getTitle() + " full movie", StandardCharsets.UTF_8);
                    m.setStreamLink("https://www.youtube.com/results?search_query=Watch+" + safeTitle);
                    mongoTemplate.save(m);
                }
            }

            recommendationSnapshotService.rebuildIfOutdated();
            log.info("Catalog bootstrap complete");
        } catch (Exception e) {
            log.error("Catalog bootstrap failed; application will continue serving with existing data", e);
        }
    }
}
