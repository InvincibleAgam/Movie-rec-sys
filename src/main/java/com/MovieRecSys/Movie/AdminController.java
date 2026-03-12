package com.MovieRecSys.Movie;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller for operational tasks.
 * Event replay, collaborative signal rebuild, and system health diagnostics.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final CollaborativeFilteringService collaborativeFilteringService;
    private final RecommendationSnapshotService snapshotService;

    public AdminController(
            CollaborativeFilteringService collaborativeFilteringService,
            RecommendationSnapshotService snapshotService
    ) {
        this.collaborativeFilteringService = collaborativeFilteringService;
        this.snapshotService = snapshotService;
    }

    @PostMapping("/rebuild-collaborative")
    public ResponseEntity<String> rebuildCollaborative() {
        collaborativeFilteringService.rebuildAll();
        return ResponseEntity.ok("Collaborative signals rebuilt");
    }

    @PostMapping("/rebuild-snapshots")
    public ResponseEntity<String> rebuildSnapshots() {
        snapshotService.rebuildAll();
        return ResponseEntity.ok("Recommendation snapshots rebuilt");
    }
}
