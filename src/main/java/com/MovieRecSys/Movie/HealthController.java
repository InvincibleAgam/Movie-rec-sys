package com.MovieRecSys.Movie;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness/readiness endpoint that actually verifies the critical dependency
 * (MongoDB) instead of returning a hardcoded "UP". Returns 503 when the database
 * is unreachable so upstreams (load balancers, Render's health check) stop
 * routing traffic to a broken instance.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final MongoTemplate mongoTemplate;

    public HealthController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, String> components = new LinkedHashMap<>();

        boolean mongoUp;
        try {
            mongoTemplate.getDb().runCommand(new Document("ping", 1));
            mongoUp = true;
        } catch (RuntimeException e) {
            mongoUp = false;
        }
        components.put("mongo", mongoUp ? "UP" : "DOWN");

        boolean healthy = mongoUp;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", healthy ? "UP" : "DOWN");
        body.put("components", components);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity
                .status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }
}
