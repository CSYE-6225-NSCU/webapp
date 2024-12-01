package com.example.webapp.controller;

import com.example.webapp.entity.Image;
import com.example.webapp.entity.User;
import com.example.webapp.dto.UserUpdateDTO;
import com.example.webapp.repository.ImageRepository;
import com.example.webapp.repository.UserRepository;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import jakarta.validation.Valid;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.regions.Region;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import org.slf4j.Logger;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;

@RestController
@RequestMapping("/v1/user")
public class UserController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ImageRepository imageRepository;
    private final SnsClient snsClient;
    private final String bucketName;
    private final String s3Region;
    private final String snsTopicArn;
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private static final StatsDClient statsDClient = new NonBlockingStatsDClient("csye6225", "localhost", 8125);

    public UserController(
            UserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder,
            ImageRepository imageRepository,
            @Value("${s3_bucket_name}") String bucketName,
            @Value("${aws.s3.region}") String s3Region,
            @Value("${aws.sns.topicArn}") String snsTopicArn) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.imageRepository = imageRepository;
        this.bucketName = bucketName;
        this.s3Region = s3Region;
        this.snsTopicArn = snsTopicArn;

        // Initialize SnsClient
        this.snsClient = SnsClient.builder()
                .region(Region.of(s3Region))
                .build();
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @org.springframework.web.bind.annotation.RequestBody User newUser) {
        Optional<User> existingUser = userRepository.findByEmail(newUser.getEmail());
        if (existingUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        statsDClient.incrementCounter("endpoint.user.create.attempt");
        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        newUser.setAccountCreated(LocalDateTime.now());
        newUser.setAccountUpdated(LocalDateTime.now());
        newUser.setVerified(false);

        userRepository.save(newUser);

        logger.info("SNS Topic ARN: {}", snsTopicArn);
        // Publish message to SNS
        try {
            Map<String, String> message = new HashMap<>();
            message.put("email", newUser.getEmail());

            ObjectMapper objectMapper = new ObjectMapper();
            String messageJson = objectMapper.writeValueAsString(message);

            PublishRequest request = PublishRequest.builder()
                    .message(messageJson)
                    .topicArn(snsTopicArn)
                    .build();
            snsClient.publish(request);

            logger.info("Published message to SNS for user: {}", newUser.getEmail());
        } catch (Exception e) {
            logger.error("Failed to publish message to SNS", e);
        }

        statsDClient.recordExecutionTime("db.operation.saveUser", System.currentTimeMillis());
        statsDClient.incrementCounter("endpoint.user.create.success");

        return ResponseEntity.status(HttpStatus.CREATED).body(newUser);
    }

    @GetMapping("/self")
    public ResponseEntity<User> getUser(Authentication authentication) {
        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (!user.isVerified()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping("/self")
    public ResponseEntity<Void> updateUser(
            @Valid @org.springframework.web.bind.annotation.RequestBody UserUpdateDTO updatedUser,
            Authentication authentication) {

        if (updatedUser.getFirstName() == null && updatedUser.getLastName() == null && updatedUser.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User existingUser = optionalUser.get();

        if (!existingUser.isVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (updatedUser.getFirstName() != null) {
            existingUser.setFirstName(updatedUser.getFirstName());
        }

        if (updatedUser.getLastName() != null) {
            existingUser.setLastName(updatedUser.getLastName());
        }

        if (updatedUser.getPassword() != null && !updatedUser.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        }

        existingUser.setAccountUpdated(LocalDateTime.now());
        userRepository.save(existingUser);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/self/pic")
    public ResponseEntity<Image> uploadProfilePic(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        User user = getCurrentAuthenticatedUser(authentication);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!user.isVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String contentType = file.getContentType();
        if (!isSupportedContentType(contentType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

            S3Client s3Client = S3Client.builder()
                    .region(Region.of(s3Region))
                    .build();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            Image image = new Image();
            image.setFileName(fileName);
            image.setUrl("https://" + bucketName + ".s3." + s3Region + ".amazonaws.com/" + fileName);
            image.setUploadDate(LocalDateTime.now());
            image.setUser(user);

            imageRepository.save(image);
            user.setImage(image);
            userRepository.save(user);

            return ResponseEntity.status(HttpStatus.CREATED).body(image);

        } catch (Exception e) {
            logger.error("Error uploading file to S3", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @DeleteMapping("/self/pic")
    public ResponseEntity<Void> deleteImage(Authentication authentication) {
        User currentUser = getCurrentAuthenticatedUser(authentication);
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!currentUser.isVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Image image = currentUser.getImage();

        if (image == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            // Initialize S3 Client
            S3Client s3Client = S3Client.builder()
                    .region(Region.of(s3Region))
                    .build();

            // Create Delete Object Request
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(image.getFileName())
                    .build();

            // Delete the object from S3
            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            logger.error("Error deleting file from S3", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Dissociate the image from the user
        currentUser.setImage(null);

        // Save the user
        userRepository.save(currentUser);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        Optional<User> optionalUser = userRepository.findByVerificationToken(token);
        if (!optionalUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid verification token.");
        }

        User user = optionalUser.get();
        if (user.getTokenExpiryTime().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Verification link has expired.");
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        user.setTokenExpiryTime(null);
        userRepository.save(user);

        return ResponseEntity.ok("Email verified successfully.");
    }

    @RequestMapping(method = {RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH})
    public ResponseEntity<Void> methodNotAllowedUser() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @RequestMapping(value = "/self", method = {RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH})
    public ResponseEntity<Void> methodNotAllowedUserSelf() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    private boolean isSupportedContentType(String contentType) {
        return contentType != null && (contentType.equals("image/png") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/jpeg"));
    }

    private User getCurrentAuthenticatedUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email).orElse(null);
    }

}
