package com.MovieRecSys.Movie;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CatalogBootstrapRunner implements ApplicationRunner {
    private final CatalogImportService catalogImportService;
    private final MovieRepository movieRepository;
    private final RecommendationSnapshotService recommendationSnapshotService;
    private final boolean seedOnStartup;

    public CatalogBootstrapRunner(
            CatalogImportService catalogImportService,
            MovieRepository movieRepository,
            RecommendationSnapshotService recommendationSnapshotService,
            @Value("${app.catalog.seed-on-startup:true}") boolean seedOnStartup
    ) {
        this.catalogImportService = catalogImportService;
        this.movieRepository = movieRepository;
        this.recommendationSnapshotService = recommendationSnapshotService;
        this.seedOnStartup = seedOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedOnStartup) {
            return;
        }

        if (movieRepository.count() == 0) {
            catalogImportService.importCatalogFromClasspath();
        }

        recommendationSnapshotService.rebuildIfOutdated();
    }
}
