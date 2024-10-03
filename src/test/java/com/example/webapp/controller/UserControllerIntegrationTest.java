package com.example.webapp.controller;

import com.example.webapp.entity.User;
import com.example.webapp.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    public void setup() {

        userRepository.deleteAll();


        testUser = new User();
        testUser.setEmail("test@test.com");
        testUser.setPassword(passwordEncoder.encode("Password123!"));
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        userRepository.save(testUser);
    }

    @Test
    public void testCreateUser_Success() throws Exception {
        String newUserJson = "{ \"first_name\": \"k\", \"last_name\": \"q\", \"email\": \"l@l.com\", \"password\": \"qqq12345\" }";

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("l@l.com"))
                .andExpect(jsonPath("$.first_name").value("k"))
                .andExpect(jsonPath("$.last_name").value("q"));
    }

    // Test case for POST /v1/user with an existing email (should fail with 400 Bad Request)
    @Test
    public void testCreateUser_EmailAlreadyExists() throws Exception {
        // Duplicate user JSON payload
        String duplicateUserJson = "{ \"first_name\": \"Test\", \"last_name\": \"User\", \"email\": \"test@test.com\", \"password\": \"Password123!\" }";

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateUserJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetUser_Success() throws Exception {
        mockMvc.perform(get("/v1/user/self")
                        .header("Authorization", "Basic " + encodeCredentials("test@test.com", "Password123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.first_name").value("Test"))
                .andExpect(jsonPath("$.last_name").value("User"));
    }

    private String encodeCredentials(String email, String password) {
        String credentials = email + ":" + password;
        return java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
    }
}
