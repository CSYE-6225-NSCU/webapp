package com.example.webapp.controller;

import com.example.webapp.entity.Image;
import com.example.webapp.entity.User;
import com.example.webapp.dto.UserUpdateDTO;
import com.example.webapp.repository.ImageRepository;
import com.example.webapp.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import javax.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/v1/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private ImageRepository imageRepository;

    @Value("${s3_bucket_name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String s3Region;

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${sns.topic.arn}")
    private String snsTopicArn;

    private SnsClient snsClient;

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private static final StatsDClient statsDClient = new NonBlockingStatsDClient("csye6225", "localhost", 8125);

    @PostConstruct
    public void init() {
        snsClient = SnsClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @org.springframework.web.bind.annotation.RequestBody User newUser) {
        statsDClient.incrementCounter("endpoint.user.create.attempt");

        Optional<User> existingUser = userRepository.findByEmail(newUser.getEmail());
        if (existingUser.isPresent()) {
            logger.warn("User with email {} already exists", newUser.getEmail());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        newUser.setAccountCreated(LocalDateTime.now());
        newUser.setAccountUpdated(LocalDateTime.now());
        newUser.setVerified(false);

        userRepository.save(newUser);

        // Publish message to SNS
        try {
            Map<String, String> message = new HashMap<>();
            message.put("email", newUser.getEmail());
            message.put("firstName", newUser.getFirstName());
            message.put("lastName", newUser.getLastName());

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
        statsDClient.incrementCounter("endpoint.user.get.attempt");

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (!user.isVerified()) {
                logger.warn("Unverified user {} attempted to access protected resource", email);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            statsDClient.incrementCounter("endpoint.user.get.success");
            return ResponseEntity.ok(user);
        } else {
            logger.warn("Unauthorized access attempt by {}", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping("/self")
    public ResponseEntity<Void> updateUser(
            @Valid @org.springframework.web.bind.annotation.RequestBody UserUpdateDTO updatedUser,
            Authentication authentication) {

        statsDClient.incrementCounter("endpoint.user.update.attempt");

        if (updatedUser.getFirstName() == null && updatedUser.getLastName() == null && updatedUser.getPassword() == null) {
            logger.warn("Update request with no fields to update");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            logger.warn("Unauthorized update attempt by {}", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User existingUser = optionalUser.get();

        if (!existingUser.isVerified()) {
            logger.warn("Unverified user {} attempted to update profile", email);
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

        statsDClient.incrementCounter("endpoint.user.update.success");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/self/pic")
    public ResponseEntity<Image> uploadProfilePic(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        statsDClient.incrementCounter("endpoint.user.pic.upload.attempt");

        User user = getCurrentAuthenticatedUser(authentication);
        if (user == null) {
            logger.warn("Unauthorized image upload attempt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!user.isVerified()) {
            logger.warn("Unverified user {} attempted to upload image", user.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String contentType = file.getContentType();
        if (!isSupportedContentType(contentType)) {
            logger.warn("Unsupported content type: {}", contentType);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

            S3Client s3Client = S3Client.builder()
                    .region(Region.of(s3Region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
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

            statsDClient.incrementCounter("endpoint.user.pic.upload.success");
            return ResponseEntity.status(HttpStatus.CREATED).body(image);

        } catch (Exception e) {
            logger.error("Error uploading file to S3", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/self/pic")
    public ResponseEntity<Void> deleteImage(Authentication authentication) {
        statsDClient.incrementCounter("endpoint.user.pic.delete.attempt");

        User currentUser = getCurrentAuthenticatedUser(authentication);
        if (currentUser == null) {
            logger.warn("Unauthorized image deletion attempt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!currentUser.isVerified()) {
            logger.warn("Unverified user {} attempted to delete image", currentUser.getEmail());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Image image = currentUser.getImage();

        if (image == null) {
            logger.warn("No image found for user {}", currentUser.getEmail());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            // Initialize S3 Client
            S3Client s3Client = S3Client.builder()
                    .region(Region.of(s3Region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        // Dissociate the image from the user
        currentUser.setImage(null);

        // Save the user
        userRepository.save(currentUser);

        statsDClient.incrementCounter("endpoint.user.pic.delete.success");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        statsDClient.incrementCounter("endpoint.user.verify.attempt");

        Optional<User> optionalUser = userRepository.findByVerificationToken(token);
        if (!optionalUser.isPresent()) {
            logger.warn("Invalid verification token: {}", token);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid verification token.");
        }

        User user = optionalUser.get();
        if (user.getTokenExpiryTime().isBefore(LocalDateTime.now())) {
            logger.warn("Expired verification token for user {}", user.getEmail());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Verification link has expired.");
        }

        user.setVerified(true);
        user.setVerificationToken(null);
        user.setTokenExpiryTime(null);
        userRepository.save(user);

        statsDClient.incrementCounter("endpoint.user.verify.success");
        return ResponseEntity.ok("Email verified successfully.");
    }

    // Handle unsupported methods
    @RequestMapping(method = {RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH})
    public ResponseEntity<Void> methodNotAllowedUser() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @RequestMapping(value = "/self", method = {RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH})
    public ResponseEntity<Void> methodNotAllowedUserSelf() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    private boolean isSupportedContentType(String contentType) {
        return contentType.equals("image/png") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/jpeg");
    }

    private User getCurrentAuthenticatedUser(Authentication authentication) {
        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);
        return optionalUser.orElse(null);
    }
}
