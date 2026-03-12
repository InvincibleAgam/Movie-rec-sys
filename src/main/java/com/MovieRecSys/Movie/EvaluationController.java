package com.MovieRecSys.Movie;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {
    private final RecommendationEvaluationService evaluationService;
    private final CollaborativeFilteringService collaborativeFilteringService;

    public EvaluationController(
            RecommendationEvaluationService evaluationService,
            CollaborativeFilteringService collaborativeFilteringService
    ) {
        this.evaluationService = evaluationService;
        this.collaborativeFilteringService = collaborativeFilteringService;
    }

    /**
     * Trigger an offline evaluation of the recommendation system.
     * Returns NDCG@K, Precision@K, Recall@K, and MAP per strategy.
     */
    @GetMapping("/run")
    public ResponseEntity<RecommendationEvaluationService.EvaluationReport> runEvaluation(
            @RequestParam(defaultValue = "10") int k
    ) {
        return ResponseEntity.ok(evaluationService.evaluate(k));
    }

    /**
     * Trigger a rebuild of collaborative filtering signals.
     */
    @PostMapping("/rebuild-collaborative")
    public ResponseEntity<String> rebuildCollaborative() {
        collaborativeFilteringService.rebuildAll();
        return ResponseEntity.ok("Collaborative signals rebuilt successfully");
    }
}
