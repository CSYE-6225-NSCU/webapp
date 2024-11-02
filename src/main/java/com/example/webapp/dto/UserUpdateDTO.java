package com.example.webapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUpdateDTO {

    @Size(max = 50, message = "First Name must be at most 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last Name must be at most 50 characters")
    private String lastName;

    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    // Getters and Setters

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
