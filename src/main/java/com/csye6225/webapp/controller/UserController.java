package com.csye6225.webapp.controller;

import com.csye6225.webapp.dto.ProfilePicResponseDto;
import com.csye6225.webapp.dto.UserRequestDto;
import com.csye6225.webapp.dto.UserResponseDto;
import com.csye6225.webapp.dto.UserUpdateRequestDto;
import com.csye6225.webapp.service.UserService;
import com.timgroup.statsd.StatsDClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/v1/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private StatsDClient statsDClient;

    // Get Authenticated User Details
    @GetMapping("/self")
    public ResponseEntity<?> getUserDetails(HttpServletRequest request) {
        // Start the timer for this API
        long start = System.currentTimeMillis();

        statsDClient.incrementCounter("api.user.getUserDetails.call_count");

        if (request.getContentLength() > 0 || request.getQueryString() != null) {
            long duration = System.currentTimeMillis() - start;
            statsDClient.recordExecutionTime("api.getUserDetails.time", duration);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userEmail = userDetails.getUsername();
        UserResponseDto userResponseDto = userService.getUserByEmail(userEmail);

        // Calculate and record execution time
        long duration = System.currentTimeMillis() - start;
        statsDClient.recordExecutionTime("api.user.getUserDetails.time", duration);

        return new ResponseEntity<>(userResponseDto, HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody UserRequestDto userRequestDto) {
        long start = System.currentTimeMillis();

        statsDClient.incrementCounter("api.user.createUser.call_count");

        UserResponseDto createdUser = userService.createUser(userRequestDto);

        long duration = System.currentTimeMillis() - start;
        statsDClient.recordExecutionTime("api.user.createUser.time", duration);

        return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
    }

    @PutMapping("/self")
    public ResponseEntity<?> updateUser(@Valid @RequestBody UserUpdateRequestDto userUpdateRequestDto) {
        long start = System.currentTimeMillis();

        statsDClient.incrementCounter("api.user.updateUser.call_count");

        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userEmail = userDetails.getUsername();

        if (!userEmail.equals(userUpdateRequestDto.getEmail())) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        userService.updateUser(userEmail, userUpdateRequestDto);

        long duration = System.currentTimeMillis() - start;
        statsDClient.recordExecutionTime("api.user.updateUser.time", duration);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @RequestMapping(value = "/self", method = RequestMethod.HEAD)
    public ResponseEntity<?> handleHead() {
        long start = System.currentTimeMillis();

        statsDClient.incrementCounter("api.user.handleHead.call_count");

        long duration = System.currentTimeMillis() - start;
        statsDClient.recordExecutionTime("api.user.handleHead.time", duration);

        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @RequestMapping(value = "/self", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        long start = System.currentTimeMillis();

        statsDClient.incrementCounter("api.user.handleOptions.call_count");

        long duration = System.currentTimeMillis() - start;
        statsDClient.recordExecutionTime("api.user.handleOptions.time", duration);

        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptionsBase() {
        long start = System.currentTimeMillis();

        statsDClient.incrementCounter("api.user.handleOptionsBase.call_count");

        long duration = System.currentTimeMillis() - start;
        statsDClient.recordExecutionTime("api.user.handleOptionsBase.time", duration);

        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @GetMapping("/self/pic")
    public ResponseEntity<?> getProfilePic(HttpServletRequest request) {
        long start = System.currentTimeMillis();
        statsDClient.incrementCounter("api.user.getProfilePic.call_count");
        if (request.getContentLength() > 0 || request.getQueryString() != null) {
            long duration = System.currentTimeMillis() - start;
            statsDClient.recordExecutionTime("api.getProfilePic.time", duration);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userEmail = userDetails.getUsername();
        long duration = System.currentTimeMillis() - start;
        statsDClient.recordExecutionTime("api.getProfilePic.time", duration);
        return userService.getProfilePic(userEmail);
    }

    @PostMapping(value = "/self/pic", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProfilePicResponseDto> addOrUpdateProfilePic(@RequestParam("profilePic") MultipartFile profilePic) throws IOException {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userEmail = userDetails.getUsername();

        statsDClient.incrementCounter("api.user.addOrUpdateProfilePic.call_count");

        return userService.uploadProfilePic(userEmail, profilePic);
    }

    @DeleteMapping("/self/pic")
    public ResponseEntity<?> deleteProfilePic() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String userEmail = userDetails.getUsername();

        statsDClient.incrementCounter("api.user.deleteProfilePic.call_count");

        return userService.deleteProfilePic(userEmail);
    }

    @RequestMapping(value = "/self/pic", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptionsUserPicSelf() {
        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @RequestMapping(value = "/self/pic", method = RequestMethod.HEAD)
    public ResponseEntity<?> handleHeadUserPicSelf() {
        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }

}
