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
import java.io.BufferedReader;
import java.io.IOException;

@RestController
@RequestMapping
public class HealthCheckController {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheckController.class);

    @Autowired
    private DataSource dataSource;

    @GetMapping({"/healthz", "/CICD"})
    public ResponseEntity<Void> healthCheck(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().mustRevalidate());
        headers.setPragma("no-cache");
        headers.add("X-Content-Type-Options", "nosniff");

        try {
            if (hasRequestBody(request) || hasQueryParameters(request)) {
                // Return 400
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .headers(headers)
                        .build();
            }
        } catch (IOException e) {
            logger.error("Error checking request body", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .headers(headers)
                    .build();
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeQuery("SELECT 1");
            // Return 200
            return ResponseEntity.ok()
                    .headers(headers)
                    .build();
        } catch (SQLException e) {
            logger.error("Database connectivity error", e);
            // Return 503
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

    private boolean hasRequestBody(HttpServletRequest request) throws IOException {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            try (BufferedReader reader = request.getReader()) {
                return reader.readLine() != null;
            }
        }
        return false;
    }

    private boolean hasQueryParameters(HttpServletRequest request) {
        return request.getQueryString() != null && !request.getQueryString().isEmpty();
    }
}
