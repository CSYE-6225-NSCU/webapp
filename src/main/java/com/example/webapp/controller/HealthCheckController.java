package com.example.webapp.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@RestController
@RequestMapping("/healthz")
public class HealthCheckController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    public ResponseEntity<Void> healthCheck(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noCache().mustRevalidate());
        headers.setPragma("no-cache");
        headers.add("X-Content-Type-Options", "nosniff");

        try {
            if (hasRequestBody(request)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).build();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).build();
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().executeQuery("SELECT 1"); // Run a test query
            return ResponseEntity.ok().headers(headers).build();
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).headers(headers).build();
        }
    }

    private boolean hasRequestBody(HttpServletRequest request) throws IOException {
        if (request.getContentLength() > 0) {
            return true;
        }
        return false;
    }
}
