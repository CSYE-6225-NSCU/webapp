package com.example.webapp.controller;

import com.example.webapp.entity.User;
import com.example.webapp.repository.UserRepository;
import com.example.webapp.service.EmailService; // Assuming EmailService is in this package
import com.example.webapp.filter.VerificationFilter; // Adjust the package as necessary
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timgroup.statsd.StatsDClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "SENDGRID_API_KEY=default_sendgrid_key", // Use default values for testing
        "s3_bucket_name=default_bucket_name",
        "AWS_REGION=us-east-1",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
@AutoConfigureMockMvc
public class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatsDClient statsDClient;

    @MockBean
    private EmailService emailService; // Mocked EmailService

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    public void setup() {
        // Clear the repository to ensure a clean state before each test
        userRepository.deleteAll();

        // Initialize a test user
        testUser = new User();
        testUser.setEmail("test@test.com");
        testUser.setPassword("Password123!");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        userRepository.save(testUser);
    }

    @Test
    public void testCreateUser_Success() throws Exception {
        String newUserJson = "{ \"first_name\": \"John\", \"last_name\": \"Doe\", \"email\": \"john.doe@example.com\", \"password\": \"password123\" }";

        mockMvc.perform(post("/v1/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.first_name").value("John"))
                .andExpect(jsonPath("$.last_name").value("Doe"));
    }

    // Additional test cases can be added here
}
