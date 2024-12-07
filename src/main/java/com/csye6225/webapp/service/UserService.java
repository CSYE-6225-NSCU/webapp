package com.csye6225.webapp.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.csye6225.webapp.dto.ProfilePicResponseDto;
import com.csye6225.webapp.dto.UserRequestDto;
import com.csye6225.webapp.dto.UserResponseDto;
import com.csye6225.webapp.dto.UserUpdateRequestDto;
import com.csye6225.webapp.exception.UserAlreadyExistsException;
import com.csye6225.webapp.model.User;
import com.csye6225.webapp.repository.UserRepository;
import com.timgroup.statsd.StatsDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private StatsDClient statsDClient;

    @Autowired
    private AmazonS3 amazonS3;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public UserResponseDto createUser(UserRequestDto userRequestDto) {
        long startExists = System.currentTimeMillis();
        logger.info("Attempting to create user with email: {}", userRequestDto.getEmail());

        if (userRepository.existsByEmail(userRequestDto.getEmail())) {
            long durationExists = System.currentTimeMillis() - startExists;
            statsDClient.recordExecutionTime("db.userRepository.existsByEmail.time", durationExists);
            logger.warn("User with email {} already exists", userRequestDto.getEmail());
            throw new UserAlreadyExistsException("User with this email already exists.");
        }
        long durationExists = System.currentTimeMillis() - startExists;
        statsDClient.recordExecutionTime("db.userRepository.existsByEmail.time", durationExists);

        User user = new User();
        user.setEmail(userRequestDto.getEmail());
        user.setFirstName(userRequestDto.getFirstName());
        user.setLastName(userRequestDto.getLastName());
        user.setPassword(passwordEncoder.encode(userRequestDto.getPassword()));

        long startSave = System.currentTimeMillis();
        userRepository.save(user);
        long durationSave = System.currentTimeMillis() - startSave;
        statsDClient.recordExecutionTime("db.userRepository.save.time", durationSave);
        logger.info("User with email {} created successfully", userRequestDto.getEmail());

        // Publish to SNS Topic
        try (SnsClient snsClient = SnsClient.create()) {
            logger.info("Publishing message to SNS topic for user verification");
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(System.getenv("SNS_TOPIC_ARN"))
                    .message("{\"email\": \"" + userRequestDto.getEmail() + "\"}")
                    .build();
            PublishResponse response = snsClient.publish(publishRequest);
            logger.info("SNS message published successfully. Message ID: {}", response.messageId());
        } catch (Exception e) {
            logger.error("Failed to publish message to SNS topic: {}", e.getMessage());
            throw new RuntimeException("Error sending email verification message.");
        }

        return mapToUserResponseDto(user);
    }

    public UserResponseDto getUserByEmail(String email) {
        long startFind = System.currentTimeMillis();
        logger.info("Fetching user with email: {}", email);

        User user = userRepository.findByEmail(email);
        long durationFind = System.currentTimeMillis() - startFind;
        statsDClient.recordExecutionTime("db.userRepository.findByEmail.time", durationFind);

        if (user == null) {
            logger.warn("User with email {} not found", email);
        }

        return mapToUserResponseDto(user);
    }

    private UserResponseDto mapToUserResponseDto(User user) {
        UserResponseDto userResponseDto = new UserResponseDto();
        userResponseDto.setId(user.getId());
        userResponseDto.setEmail(user.getEmail());
        userResponseDto.setFirstName(user.getFirstName());
        userResponseDto.setLastName(user.getLastName());
        userResponseDto.setAccountCreated(user.getAccountCreated().toString());
        userResponseDto.setAccountUpdated(user.getAccountUpdated().toString());

        return userResponseDto;
    }

    public void updateUser(String email, UserUpdateRequestDto userUpdateRequestDto) {
        long startFind = System.currentTimeMillis();
        logger.info("Updating user with email: {}", email);

        User user = userRepository.findByEmail(email);
        long durationFind = System.currentTimeMillis() - startFind;
        statsDClient.recordExecutionTime("db.userRepository.findByEmail.time", durationFind);

        user.setFirstName(userUpdateRequestDto.getFirstName());
        user.setLastName(userUpdateRequestDto.getLastName());
        user.setPassword(passwordEncoder.encode(userUpdateRequestDto.getPassword()));

        long startSave = System.currentTimeMillis();
        userRepository.save(user);
        long durationSave = System.currentTimeMillis() - startSave;
        statsDClient.recordExecutionTime("db.userRepository.save.time", durationSave);
        logger.info("User with email {} updated successfully", email);
    }

    public ResponseEntity<ProfilePicResponseDto> uploadProfilePic(String userEmail, MultipartFile file) throws IOException {
        long startFind = System.currentTimeMillis();
        User user = userRepository.findByEmail(userEmail);
        long durationFind = System.currentTimeMillis() - startFind;
        statsDClient.recordExecutionTime("db.userRepository.findByEmail.time", durationFind);

        if (user.getProfilePicUrl() != null) {
            logger.warn("User with email {} already has a profile picture", userEmail);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        logger.info("Uploading profile picture for user with email: {}", userEmail);

        // Check file content type
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/png") && !contentType.equals("image/jpeg") && !contentType.equals("image/jpg"))) {
            logger.warn("Invalid file type: {}", contentType);
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        String fileName = file.getOriginalFilename();
        String key = "profile-pictures/" + user.getId() + "/" + fileName;
        String uniqueId = UUID.randomUUID().toString();

        long startS3Put = System.currentTimeMillis();
        amazonS3.putObject(new PutObjectRequest(bucketName, key, file.getInputStream(), null));
        long durationS3Put = System.currentTimeMillis() - startS3Put;
        statsDClient.recordExecutionTime("aws.s3.putObject.time", durationS3Put);

        user.setProfilePicUrl(key);

        long startSave = System.currentTimeMillis();
        userRepository.save(user);
        long durationSave = System.currentTimeMillis() - startSave;
        statsDClient.recordExecutionTime("db.userRepository.save.time", durationSave);

        logger.info("Profile picture uploaded successfully for user: {}", userEmail);

        ProfilePicResponseDto responseDto = new ProfilePicResponseDto(fileName, uniqueId, amazonS3.getUrl(bucketName, key).toString(), LocalDate.now(), user.getId().toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }

    public ResponseEntity<?> deleteProfilePic(String userEmail) {
        long startFind = System.currentTimeMillis();
        User user = userRepository.findByEmail(userEmail);
        long durationFind = System.currentTimeMillis() - startFind;
        statsDClient.recordExecutionTime("db.userRepository.findByEmail.time", durationFind);

        String key = user.getProfilePicUrl();

        if (key == null || !amazonS3.doesObjectExist(bucketName, key)) {
            logger.warn("No profile picture found for user with email: {}", userEmail);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        logger.info("Deleting profile picture for user with email: {}", userEmail);

        long startS3Delete = System.currentTimeMillis();
        amazonS3.deleteObject(new DeleteObjectRequest(bucketName, key));
        long durationS3Delete = System.currentTimeMillis() - startS3Delete;
        statsDClient.recordExecutionTime("aws.s3.deleteObject.time", durationS3Delete);

        user.setProfilePicUrl(null);

        long startSave = System.currentTimeMillis();
        userRepository.save(user);
        long durationSave = System.currentTimeMillis() - startSave;
        statsDClient.recordExecutionTime("db.userRepository.save.time", durationSave);

        logger.info("Profile picture deleted successfully for user: {}", userEmail);

        return ResponseEntity.noContent().build();
    }

    public ResponseEntity<?> getProfilePic(String userEmail) {
        long startFind = System.currentTimeMillis();
        User user = userRepository.findByEmail(userEmail);
        long durationFind = System.currentTimeMillis() - startFind;
        statsDClient.recordExecutionTime("db.userRepository.findByEmail.time", durationFind);

        String key = user.getProfilePicUrl();

        if (key == null || !amazonS3.doesObjectExist(bucketName, key)) {
            logger.warn("No profile picture found for user with email: {}", userEmail);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        logger.info("Profile picture found for user with email: {}", userEmail);

        long startS3GetUrl = System.currentTimeMillis();
        String url = amazonS3.getUrl(bucketName, key).toString();
        long durationS3GetUrl = System.currentTimeMillis() - startS3GetUrl;
        statsDClient.recordExecutionTime("aws.s3.getUrl.time", durationS3GetUrl);

        ProfilePicResponseDto responseDto = new ProfilePicResponseDto(user.getProfilePicUrl(), user.getId().toString(), url, LocalDate.now(), user.getId().toString());

        return ResponseEntity.ok(responseDto);
    }
}
