package com.example.webapp.controller;

import com.example.webapp.entity.User;
import com.example.webapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.Optional;

@RestController
@RequestMapping("/v1/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody User user) {
        // Check if user with the same email already exists
        Optional<User> existingUser = userRepository.findByEmail(user.getEmail());
        if (existingUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("User with this email already exists.");
        }

        // Ignore account_created and account_updated if provided
        user.setAccountCreated(null);
        user.setAccountUpdated(null);

        // Hash the password using BCrypt
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Save the user
        User savedUser = userRepository.save(user);

        // Do not return the password in the response
        savedUser.setPassword(null);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
        // Ensure that the method returns a ResponseEntity
    }

    @GetMapping
    public ResponseEntity<?> getUser(Authentication authentication) {
        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setPassword(null); // Exclude password from response

            return ResponseEntity.ok(user);
        } else {
            // Added return statement to handle the case when user is not found
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping
    public ResponseEntity<?> updateUser(
            @Valid @RequestBody User updatedUser,
            Authentication authentication) {

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User existingUser = optionalUser.get();

        // Disallow updates to email and timestamps
        if (updatedUser.getEmail() != null &&
                !updatedUser.getEmail().equals(existingUser.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Email cannot be updated.");
        }

        if (updatedUser.getAccountCreated() != null ||
                updatedUser.getAccountUpdated() != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot update account_created or account_updated.");
        }

        // Update allowed fields
        existingUser.setFirstName(updatedUser.getFirstName());
        existingUser.setLastName(updatedUser.getLastName());

        if (updatedUser.getPassword() != null &&
                !updatedUser.getPassword().isEmpty()) {
            existingUser.setPassword(
                    passwordEncoder.encode(updatedUser.getPassword()));
        }

        userRepository.save(existingUser);
        existingUser.setPassword(null); // Exclude password from response

        return ResponseEntity.ok(existingUser);
        // Added return statement to ensure the method returns a ResponseEntity
    }
}
