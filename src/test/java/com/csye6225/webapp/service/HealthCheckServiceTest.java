package com.csye6225.webapp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import com.timgroup.statsd.StatsDClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.MockitoAnnotations.openMocks;

class HealthCheckServiceTest {

    @InjectMocks
    private HealthCheckService healthCheckService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private StatsDClient statsDClient;

    @BeforeEach
    void setUp() {
        openMocks(this);
    }

    @Test
    void testIsDatabaseConnected_success() {
        // Simulate successful execution (doNothing since it's a void method)
        doNothing().when(jdbcTemplate).execute(Mockito.anyString());
        doNothing().when(statsDClient).recordExecutionTime(Mockito.anyString(), Mockito.anyLong());

        boolean isConnected = healthCheckService.isDatabaseConnected();

        assertTrue(isConnected);
    }

    @Test
    void testIsDatabaseConnected_failure() {
        // Simulate an exception being thrown during database connection
        doThrow(new RuntimeException("Connection failed")).when(jdbcTemplate).execute(Mockito.anyString());
        doNothing().when(statsDClient).recordExecutionTime(Mockito.anyString(), Mockito.anyLong());

        boolean isConnected = healthCheckService.isDatabaseConnected();

        assertFalse(isConnected);
    }
}
