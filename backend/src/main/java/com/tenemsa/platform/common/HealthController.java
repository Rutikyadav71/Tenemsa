package com.tenemsa.platform.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liveness and readiness in one public endpoint, for hosts, load balancers and Docker health checks.
 * 200 means the app is up and can reach its database; 503 means the database cannot be reached.
 * It deliberately says nothing else (no versions, no error text), because anyone can call it.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> body = new LinkedHashMap<>();
        boolean dbUp;
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            dbUp = one != null && one == 1;
        } catch (Exception e) {
            dbUp = false;
        }
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("database", dbUp ? "UP" : "DOWN");
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
