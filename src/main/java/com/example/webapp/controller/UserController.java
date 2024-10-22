package com.example.webapp.controller;

import com.example.webapp.entity.User;
import com.example.webapp.dto.UserUpdateDTO;
import com.example.webapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/v1/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;


    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody User newUser) {


        Optional<User> existingUser = userRepository.findByEmail(newUser.getEmail());
        if (existingUser.isPresent()) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }


        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));


        newUser.setAccountCreated(LocalDateTime.now());
        newUser.setAccountUpdated(LocalDateTime.now());

        // Save the new user
        userRepository.save(newUser);


        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
    }


    @GetMapping("/self")
    public ResponseEntity<User> getUser(Authentication authentication) {
        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }


    @PutMapping("/self")
    public ResponseEntity<Void> updateUser(
            @Valid @RequestBody UserUpdateDTO updatedUser,
            Authentication authentication) {


        if (updatedUser.getFirstName() == null && updatedUser.getLastName() == null && updatedUser.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        User existingUser = optionalUser.get();


        if (updatedUser.getFirstName() != null) {
            existingUser.setFirstName(updatedUser.getFirstName());
        }

        if (updatedUser.getLastName() != null) {
            existingUser.setLastName(updatedUser.getLastName());
        }

        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            existingUser.setPassword(
                    passwordEncoder.encode(updatedUser.getPassword()));
        }


        existingUser.setAccountUpdated(LocalDateTime.now());

        userRepository.save(existingUser);


        return ResponseEntity.noContent().build();
    }

        @RequestMapping(method = {RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH})
        public ResponseEntity<Void> methodNotAllowedUser() {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }

        // Handle unsupported methods for /v1/user/self
        @RequestMapping(value = "/self", method = {RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH})
        public ResponseEntity<Void> methodNotAllowedUserSelf() {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }


}
