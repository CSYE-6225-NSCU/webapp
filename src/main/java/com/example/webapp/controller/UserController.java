package com.example.webapp.controller;
import com.example.webapp.entity.Image;
import com.example.webapp.entity.User;
import com.example.webapp.dto.UserUpdateDTO;
import com.example.webapp.repository.ImageRepository;
import com.example.webapp.repository.UserRepository;
import com.example.webapp.service.EmailService;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
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
    private EmailService emailService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private ImageRepository imageRepository;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);


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

        // Save the new user
        userRepository.save(newUser);

        // Send email
        try {
            String subject = "Welcome to the App!";
            String content = "Thank you for registering.";
            emailService.sendEmail(newUser.getEmail(), subject, content);
        } catch (Exception e) {
            // Handle email sending failure
            logger.error("Failed to send email", e);
        }
        long dbStartTime = System.currentTimeMillis();
        userRepository.save(newUser);
        statsDClient.recordExecutionTime("db.operation.saveUser", System.currentTimeMillis() - dbStartTime);
        statsDClient.incrementCounter("endpoint.user.create.success");

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
            @Valid @org.springframework.web.bind.annotation.RequestBody UserUpdateDTO updatedUser,
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



        // Other methods here...

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

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = optionalUser.get();

        // Validate file type
        String contentType = file.getContentType();
        if (!isSupportedContentType(contentType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            // Generate unique file name
            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();

            // Upload file to S3
            S3Client s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .build();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

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

            return ResponseEntity.status(HttpStatus.CREATED).body(image);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Endpoint to delete image
    @DeleteMapping("/self/pic")
    public ResponseEntity<Void> deleteProfilePic(Authentication authentication) {

        String email = authentication.getName();
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (!optionalUser.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = optionalUser.get();
        Image image = user.getImage();

        if (image == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        try {
            // Delete from S3
            S3Client s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .build();

            s3Client.deleteObject(b -> b.bucket(bucketName).key(image.getFileName()));

            // Delete from database
            imageRepository.delete(image);

            // Remove image from user
            user.setImage(null);
            userRepository.save(user);

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper method to validate content type
    private boolean isSupportedContentType(String contentType) {
        return contentType.equals("image/png") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/jpeg");
    }
    private static final StatsDClient statsDClient = new NonBlockingStatsDClient(
            "csye6225",
            "localhost",
            8125
    );


}
