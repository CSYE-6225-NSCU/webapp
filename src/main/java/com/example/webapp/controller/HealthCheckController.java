package com.example.webapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import com.timgroup.statsd.StatsDClient;

@RestController
@RequestMapping("/healthz")
public class HealthCheckController {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StatsDClient statsDClient;

    @GetMapping
    public ResponseEntity<Void> healthCheck(HttpServletRequest request) {
        statsDClient.incrementCounter("endpoint.healthz.attempt");
        long startTime = System.currentTimeMillis();

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().mustRevalidate());
        headers.setPragma("no-cache");
        headers.add("X-Content-Type-Options", "nosniff");

        try {
            if (requestHasBodyOrParameters(request)) {
                logger.warn("Health check request contains body or query parameters");
                statsDClient.incrementCounter("endpoint.healthz.failure");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .headers(headers)
                        .build();
            }
        } catch (IOException e) {
            logger.error("Error checking request body", e);
            statsDClient.incrementCounter("endpoint.healthz.failure");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .headers(headers)
                    .build();
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeQuery("SELECT 1");
            logger.info("Health check passed");
            statsDClient.incrementCounter("endpoint.healthz.success");
            statsDClient.recordExecutionTime("endpoint.healthz.duration", System.currentTimeMillis() - startTime);
            return ResponseEntity.ok()
                    .headers(headers)
                    .build();
        } catch (SQLException e) {
            logger.error("Database connectivity error", e);
            statsDClient.incrementCounter("endpoint.healthz.failure");
            statsDClient.recordExecutionTime("endpoint.healthz.duration", System.currentTimeMillis() - startTime);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .headers(headers)
                    .build();
        }
    }

    // Handle unsupported HTTP methods
    @RequestMapping(method = { RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.HEAD,
            RequestMethod.OPTIONS, RequestMethod.PATCH })
    public ResponseEntity<Void> methodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    private boolean requestHasBodyOrParameters(HttpServletRequest request) throws IOException {
        return hasRequestBody(request) || hasQueryParameters(request);
    }

    private boolean hasRequestBody(HttpServletRequest request) throws IOException {
        return request.getInputStream().available() > 0;
    }

    private boolean hasQueryParameters(HttpServletRequest request) {
        return request.getQueryString() != null && !request.getQueryString().isEmpty();
    }
}
