package com.example.webapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty; // Updated import
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is mandatory")
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // Updated annotation
    @NotBlank(message = "Password is mandatory")
    private String password;

    @Column(nullable = false, name = "first_name")
    @NotBlank(message = "First Name is mandatory")
    private String firstName;

    @Column(nullable = false, name = "last_name")
    @NotBlank(message = "Last Name is mandatory")
    private String lastName;

    @Column(updatable = false, name = "account_created")
    private LocalDateTime accountCreated;

    @Column(name = "account_updated")
    private LocalDateTime accountUpdated;

    // Getters and Setters

    // id
    public Long getId() {
        return id;
    }

    // email
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // password
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // firstName
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    // lastName
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    // accountCreated
    public LocalDateTime getAccountCreated() {
        return accountCreated;
    }

    public void setAccountCreated(LocalDateTime accountCreated) {
        this.accountCreated = accountCreated;
    }

    // accountUpdated
    public LocalDateTime getAccountUpdated() {
        return accountUpdated;
    }

    public void setAccountUpdated(LocalDateTime accountUpdated) {
        this.accountUpdated = accountUpdated;
    }

    // Lifecycle Callbacks

    @PrePersist
    protected void onCreate() {
        this.accountCreated = LocalDateTime.now();
        this.accountUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.accountUpdated = LocalDateTime.now();
    }
}
