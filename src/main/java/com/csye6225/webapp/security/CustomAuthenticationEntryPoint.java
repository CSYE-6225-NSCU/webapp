package com.csye6225.webapp.security;

import com.csye6225.webapp.exception.DatabaseAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        Throwable cause = authException.getCause();
        response.setContentType("application/json");
        if (cause instanceof DatabaseAuthenticationException) {
            // Handle database unavailability separately and return 503 Service Unavailable
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } else {
            // Handle normal authentication failure (invalid username/password)
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
