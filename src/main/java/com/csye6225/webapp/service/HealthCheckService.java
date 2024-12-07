package com.csye6225.webapp.service;

import com.timgroup.statsd.StatsDClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthCheckService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StatsDClient statsDClient;

    public boolean isDatabaseConnected() {
        // Start timer for the database connection check
        long start = System.currentTimeMillis();

        try {
            jdbcTemplate.execute("SELECT 1");

            // Record execution time
            long duration = System.currentTimeMillis() - start;
            statsDClient.recordExecutionTime("db.isDatabaseConnected.time", duration);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
