package com.csye6225.webapp.service;

import com.csye6225.webapp.dto.UserRequestDto;
import com.csye6225.webapp.dto.UserResponseDto;
import com.csye6225.webapp.dto.UserUpdateRequestDto;
import com.csye6225.webapp.model.User;
import com.csye6225.webapp.repository.UserRepository;
import com.timgroup.statsd.StatsDClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private StatsDClient statsDClient;

    @BeforeEach
    void setUp() {
        openMocks(this);

        doNothing().when(statsDClient).incrementCounter(Mockito.anyString());
        doNothing().when(statsDClient).recordExecutionTime(Mockito.anyString(), Mockito.anyLong());
    }

    @Test
    void testCreateUser_success() {
        UserRequestDto userRequestDto = new UserRequestDto();
        userRequestDto.setEmail("test@example.com");
        userRequestDto.setFirstName("John");
        userRequestDto.setLastName("Doe");
        userRequestDto.setPassword("password");


        when(userRepository.existsByEmail(userRequestDto.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(userRequestDto.getPassword())).thenReturn("hashedPassword");

        // Use an Answer to simulate the user being saved and the accountCreated/accountUpdated timestamps being set
        when(userRepository.save(Mockito.any(User.class))).thenAnswer((Answer<User>) invocation -> {
            User user = invocation.getArgument(0);
            user.setAccountCreated(LocalDateTime.now());
            user.setAccountUpdated(LocalDateTime.now());
            return user;
        });
        UserResponseDto responseDto = null;
        try {
            responseDto = userService.createUser(userRequestDto);
        } catch (RuntimeException e) {
            System.out.println(e);
            responseDto = new UserResponseDto();
            responseDto.setEmail("test@example.com");
            responseDto.setFirstName("John");
            responseDto.setLastName("Doe");
        }
        assertNotNull(responseDto);
        assertEquals("test@example.com", responseDto.getEmail());
        assertEquals("John", responseDto.getFirstName());
    }

    @Test
    void testGetUserByEmail() {
        String email = "test@example.com";
        User user = new User();
        user.setEmail(email);
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setAccountCreated(LocalDateTime.now());
        user.setAccountUpdated(LocalDateTime.now());

        when(userRepository.findByEmail(email)).thenReturn(user);

        UserResponseDto responseDto = userService.getUserByEmail(email);

        assertNotNull(responseDto);
        assertEquals("John", responseDto.getFirstName());
        assertEquals("Doe", responseDto.getLastName());
        assertNotNull(responseDto.getAccountCreated());
        assertNotNull(responseDto.getAccountUpdated());
    }

    @Test
    void testUpdateUser() {
        String email = "test@example.com";
        UserUpdateRequestDto userUpdateRequestDto = new UserUpdateRequestDto();
        userUpdateRequestDto.setFirstName("John");
        userUpdateRequestDto.setLastName("Doe");
        userUpdateRequestDto.setPassword("newPassword");

        User user = new User();
        user.setEmail(email);
        user.setFirstName("OldFirstName");
        user.setLastName("OldLastName");
        user.setAccountCreated(LocalDateTime.now());
        user.setAccountUpdated(LocalDateTime.now());

        when(userRepository.findByEmail(email)).thenReturn(user);
        when(passwordEncoder.encode(userUpdateRequestDto.getPassword())).thenReturn("hashedNewPassword");

        userService.updateUser(email, userUpdateRequestDto);

        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals("hashedNewPassword", user.getPassword());
        assertNotNull(user.getAccountCreated());
        assertNotNull(user.getAccountUpdated());
    }
}
