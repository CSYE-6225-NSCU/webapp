package com.csye6225.webapp.controller;

import com.csye6225.webapp.service.EmailVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/user")
public class EmailVerificationController {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationController.class);

    @Autowired
    private EmailVerificationService emailVerificationService;

    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        logger.info("Received email verification request for token: {}", token);
        try {
            String message = emailVerificationService.verifyEmail(token);
            logger.info("Email verification successful for token: {}", token);
            return ResponseEntity.ok(message);
        } catch (IllegalArgumentException e) {
            logger.error("Error verifying email for token {}: {}", token, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
