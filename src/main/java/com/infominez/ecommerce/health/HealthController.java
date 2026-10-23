package com.infominez.ecommerce.health;

import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
    private final HealthEndpoint endpoint;
    public HealthController(HealthEndpoint endpoint) { this.endpoint = endpoint; }
    public record HealthResponse(String status) {}

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> liveness() { return response(endpoint.healthForPath("liveness")); }

    @GetMapping("/ready")
    public ResponseEntity<HealthResponse> readiness() { return response(endpoint.healthForPath("readiness")); }

    private ResponseEntity<HealthResponse> response(HealthComponent health) {
        boolean up = health != null && Status.UP.equals(health.getStatus());
        return ResponseEntity.status(up ? 200 : 503).body(new HealthResponse(up ? "UP" : "DOWN"));
    }
}
