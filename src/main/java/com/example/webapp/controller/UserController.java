package com.example.webapp.controller;

import com.example.webapp.entity.Image;
import com.example.webapp.entity.User;
import com.example.webapp.dto.UserUpdateDTO;
import com.example.webapp.repository.ImageRepository;
import com.example.webapp.repository.UserRepository;
import com.example.webapp.service.EmailService;
import com.timgroup.statsd.StatsDClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/v1/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StatsDClient statsDClient;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private ImageRepository imageRepository;

    @Value("${s3_bucket_name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @PostMapping
    public ResponseEntity<User> createUser(@Valid @org.springframework.web.bind.annotation.RequestBody User newUser) {
        statsDClient.incrementCounter("endpoint.user.create.attempt");
        long apiStartTime = System.currentTimeMillis();

        Optional<User> existingUser = userRepository.findByEmail(newUser.getEmail());
        if (existingUser.isPresent()) {
            logger.warn("Attempt to create user with existing email: {}", newUser.getEmail());
            statsDClient.incrementCounter("endpoint.user.create.failure");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        newUser.setAccountCreated(LocalDateTime.now());
        newUser.setAccountUpdated(LocalDateTime.now());

        try {
            long dbStartTime = System.currentTimeMillis();
            userRepository.save(newUser);
            statsDClient.recordExecutionTime("db.operation.saveUser", System.currentTimeMillis() - dbStartTime);
            statsDClient.incrementCounter("endpoint.user.create.success");
            logger.info("User created successfully: {}", newUser.getEmail());

            // Send email
            try {
                String subject = "Welcome to the App!";
                String content = "Thank you for registering.";
                emailService.sendEmail(newUser.getEmail(), subject, content);
            } catch (Exception e) {
                logger.error("Failed to send email to {}: {}", newUser.getEmail(), e.getMessage(), e);
            }

            statsDClient.recordExecutionTime("endpoint.user.create.duration", System.currentTimeMillis() - apiStartTime);
            return ResponseEntity.status(HttpStatus.CREATED).body(newUser);

        } catch (Exception e) {
            logger.error("Error creating user {}: {}", newUser.getEmail(), e.getMessage(), e);
            statsDClient.incrementCounter("endpoint.user.create.failure");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/self")
    public ResponseEntity<User> getUser(Authentication authentication) {
        statsDClient.incrementCounter("endpoint.user.get.attempt");
        long apiStartTime = System.currentTimeMillis();

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            statsDClient.incrementCounter("endpoint.user.get.success");
            statsDClient.recordExecutionTime("endpoint.user.get.duration", System.currentTimeMillis() - apiStartTime);
            return ResponseEntity.ok(user);
        } else {
            logger.warn("User not found: {}", email);
            statsDClient.incrementCounter("endpoint.user.get.failure");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PutMapping("/self")
    public ResponseEntity<Void> updateUser(
            @Valid @org.springframework.web.bind.annotation.RequestBody UserUpdateDTO updatedUser,
            Authentication authentication) {

        statsDClient.incrementCounter("endpoint.user.update.attempt");
        long apiStartTime = System.currentTimeMillis();

        if (updatedUser.getFirstName() == null && updatedUser.getLastName() == null && updatedUser.getPassword() == null) {
            logger.warn("Update request with no fields to update");
            statsDClient.incrementCounter("endpoint.user.update.failure");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            logger.warn("User not found for update: {}", email);
            statsDClient.incrementCounter("endpoint.user.update.failure");
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
            existingUser.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
        }

        existingUser.setAccountUpdated(LocalDateTime.now());

        try {
            long dbStartTime = System.currentTimeMillis();
            userRepository.save(existingUser);
            statsDClient.recordExecutionTime("db.operation.updateUser", System.currentTimeMillis() - dbStartTime);
            statsDClient.incrementCounter("endpoint.user.update.success");
            logger.info("User updated successfully: {}", email);

            statsDClient.recordExecutionTime("endpoint.user.update.duration", System.currentTimeMillis() - apiStartTime);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            logger.error("Error updating user {}: {}", email, e.getMessage(), e);
            statsDClient.incrementCounter("endpoint.user.update.failure");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Handle unsupported methods for /v1/user
    @RequestMapping(method = {RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH})
    public ResponseEntity<Void> methodNotAllowedUser() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    // Handle unsupported methods for /v1/user/self
    @RequestMapping(value = "/self", method = {RequestMethod.DELETE, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH})
    public ResponseEntity<Void> methodNotAllowedUserSelf() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @PostMapping("/self/pic")
    public ResponseEntity<Image> uploadProfilePic(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        statsDClient.incrementCounter("endpoint.user.uploadPic.attempt");
        long apiStartTime = System.currentTimeMillis();

        String email = authentication.getName();
        logger.info("Uploading profile picture for user: {}", email);
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            logger.warn("User not found: {}", email);
            statsDClient.incrementCounter("endpoint.user.uploadPic.failure");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = optionalUser.get();

        // Validate file type
        String contentType = file.getContentType();
        if (!isSupportedContentType(contentType)) {
            logger.warn("Unsupported file type: {}", contentType);
            statsDClient.incrementCounter("endpoint.user.uploadPic.failure");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            // Check if user already has an image
            if (user.getImage() != null) {
                // Delete existing image
                deleteImageFromS3(user.getImage().getFileName());
                imageRepository.delete(user.getImage());
                user.setImage(null);
            }

            // Generate unique file name
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

            // Upload file to S3
            uploadImageToS3(file, fileName, contentType);

            // Create Image metadata
            Image image = new Image();
            image.setFileName(fileName);
            image.setUrl("https://" + bucketName + ".s3." + region + ".amazonaws.com/" + fileName);
            image.setUploadDate(LocalDateTime.now());
            image.setUser(user);

            // Save image metadata
            imageRepository.save(image);

            // Update user's image
            user.setImage(image);
            userRepository.save(user);

            statsDClient.incrementCounter("endpoint.user.uploadPic.success");
            statsDClient.recordExecutionTime("endpoint.user.uploadPic.duration", System.currentTimeMillis() - apiStartTime);
            logger.info("Profile picture uploaded successfully for user: {}", email);

            return ResponseEntity.status(HttpStatus.CREATED).body(image);

        } catch (Exception e) {
            logger.error("Error uploading profile picture for user {}: {}", email, e.getMessage(), e);
            statsDClient.incrementCounter("endpoint.user.uploadPic.failure");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/self/pic")
    public ResponseEntity<Void> deleteProfilePic(Authentication authentication) {
        statsDClient.incrementCounter("endpoint.user.deletePic.attempt");
        long apiStartTime = System.currentTimeMillis();

        String email = authentication.getName();
        logger.info("Attempting to delete profile picture for user: {}", email);
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            logger.warn("User not found: {}", email);
            statsDClient.incrementCounter("endpoint.user.deletePic.failure");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = optionalUser.get();
        Image image = user.getImage();

        if (image == null) {
            logger.warn("No profile picture found for user: {}", email);
            statsDClient.incrementCounter("endpoint.user.deletePic.failure");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            // Delete from S3
            deleteImageFromS3(image.getFileName());
            logger.info("Deleted image from S3");

            // Delete from database
            imageRepository.delete(image);
            logger.info("Deleted image record from database");

            // Remove image from user
            user.setImage(null);
            userRepository.save(user);
            logger.info("Updated user record to remove image");

            statsDClient.incrementCounter("endpoint.user.deletePic.success");
            statsDClient.recordExecutionTime("endpoint.user.deletePic.duration", System.currentTimeMillis() - apiStartTime);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            logger.error("Error deleting profile picture for user {}: {}", email, e.getMessage(), e);
            statsDClient.incrementCounter("endpoint.user.deletePic.failure");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper method to validate content type
    private boolean isSupportedContentType(String contentType) {
        return contentType != null && (contentType.equals("image/png") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/jpeg"));
    }

    // Helper method to upload image to S3
    private void uploadImageToS3(MultipartFile file, String fileName, String contentType) throws Exception {
        S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .contentType(contentType)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
    }

    // Helper method to delete image from S3
    private void deleteImageFromS3(String fileName) throws Exception {
        S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();

        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
    }
}
