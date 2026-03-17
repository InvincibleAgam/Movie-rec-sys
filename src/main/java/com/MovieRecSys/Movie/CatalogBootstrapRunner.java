package com.MovieRecSys.Movie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CatalogBootstrapRunner implements ApplicationRunner {
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
    }
}
