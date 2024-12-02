package com.csye6225.webapp.security;

import com.csye6225.webapp.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.userdetails.User;

import java.io.IOException;

@Component
public class EmailVerificationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository; // Inject your UserRepository here

    public EmailVerificationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            // Check if the principal is of type User
            if (principal instanceof User) {
                String email = ((User) principal).getUsername(); // Get email from User object

                // Fetch the User entity from the database
                com.csye6225.webapp.model.User dbUser = userRepository.findByEmail(email);
                if (dbUser != null && !dbUser.getEmailVerified()) {
                    // If the user exists and is not verified, block the request
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
        }
        // Continue the filter chain
        filterChain.doFilter(request, response);
    }
}
