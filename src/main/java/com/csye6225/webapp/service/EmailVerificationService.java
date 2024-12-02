package com.csye6225.webapp.service;

import com.csye6225.webapp.model.SentEmail;
import com.csye6225.webapp.model.User;
import com.csye6225.webapp.repository.SentEmailRepository;
import com.csye6225.webapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EmailVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationService.class);

    @Autowired
    private SentEmailRepository sentEmailRepository;

    @Autowired
    private UserRepository userRepository;

    public String verifyEmail(String token) {
        logger.info("Verifying email with token: {}", token);

        // Retrieve the email verification entry by token
        SentEmail sentEmail = sentEmailRepository.findByToken(token)
                .orElseThrow(() -> {
                    logger.warn("Invalid token: {}", token);
                    return new IllegalArgumentException("Invalid token");
                });

        logger.info("Email token found for email: {}", sentEmail.getEmail());

        // Check if the token is expired
        if (sentEmail.getSentAt().toInstant().plusSeconds(120).isBefore(Instant.now())) {
            logger.warn("Token expired for email: {}", sentEmail.getEmail());
            throw new IllegalArgumentException("Token has expired");
        }

        // Mark the user as verified
        User user = userRepository.findByEmail(sentEmail.getEmail());
        if (user == null) {
            logger.error("User not found for email: {}", sentEmail.getEmail());
            throw new IllegalArgumentException("User not found");
        }

        logger.info("Marking user as verified for email: {}", user.getEmail());
        user.setEmailVerified(true);
        userRepository.save(user);

        sentEmail.setStatus("VERIFIED");
        sentEmailRepository.save(sentEmail);

        logger.info("Email verification completed for email: {}", user.getEmail());
        return "Email successfully verified!";
    }
}
