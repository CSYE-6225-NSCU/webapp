package com.csye6225.webapp.controller;

import com.csye6225.webapp.service.HealthCheckService;
import com.timgroup.statsd.StatsDClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/healthz")
public class HealthCheckController {

    @Autowired
    private HealthCheckService healthCheckService;

    @Autowired
    private StatsDClient statsDClient;

    @GetMapping
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<Void> healthCheck(HttpServletRequest request) {
        // Start the timer for this API
        long start = System.currentTimeMillis();

        statsDClient.incrementCounter("api.healthz.call_count");

        if (request.getContentLength() > 0 || request.getQueryString() != null) {
            long duration = System.currentTimeMillis() - start;
            statsDClient.recordExecutionTime("api.healthz.time", duration);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        boolean isDbConnected = healthCheckService.isDatabaseConnected();

        long duration = System.currentTimeMillis() - start;
        statsDClient.recordExecutionTime("api.healthz.time", duration);

        if (isDbConnected) {
            return ResponseEntity.ok().header("Cache-Control", "no-cache").build();
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Cache-Control", "no-cache")
                    .build();
        }
    }

}
