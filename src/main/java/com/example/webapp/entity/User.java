package com.example.webapp.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty("id")
    private Long id;

    @Column(unique = true, nullable = false)
    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is mandatory")
    @Pattern(regexp = "^[\\w-\\.]+@[\\w-]+\\.[a-z]{2,3}$", message = "Invalid email address")
    @JsonProperty("email")
    private String email;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank(message = "Password is mandatory")
    private String password;

    @Column(nullable = false)
    @JsonProperty("first_name")
    @NotBlank(message = "First Name is mandatory")
    private String firstName;

    @Column(nullable = false)
    @JsonProperty("last_name")
    @NotBlank(message = "Last Name is mandatory")
    private String lastName;

    @Column(updatable = false, name = "account_created")
    @JsonProperty("account_created")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime accountCreated;

    @Column(name = "account_updated")
    @JsonProperty("account_updated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime accountUpdated;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // Manages the reference to avoid recursive serialization
    private Image image;

    // New fields added for email verification
    @Column(nullable = false)
    @JsonProperty("verified")
    private boolean verified;

    @Column(name = "verification_token")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String verificationToken;

    @Column(name = "token_expiry_time")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private LocalDateTime tokenExpiryTime;

    public User() {
    }

    public User(String email, String password, String firstName, String lastName) {
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.verified = false; // Default to false on creation
    }

    // Getters and Setters for all fields, including new ones

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    // Password field
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // First Name
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    // Last Name
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    // Account Created
    public LocalDateTime getAccountCreated() {
        return accountCreated;
    }

    public void setAccountCreated(LocalDateTime accountCreated) {
        this.accountCreated = accountCreated;
    }

    // Account Updated
    public LocalDateTime getAccountUpdated() {
        return accountUpdated;
    }

    public void setAccountUpdated(LocalDateTime accountUpdated) {
        this.accountUpdated = accountUpdated;
    }

    // Image
    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
        if (image != null) {
            image.setUser(this);
        }
    }

    // Verified
    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    // Verification Token
    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    // Token Expiry Time
    public LocalDateTime getTokenExpiryTime() {
        return tokenExpiryTime;
    }

    public void setTokenExpiryTime(LocalDateTime tokenExpiryTime) {
        this.tokenExpiryTime = tokenExpiryTime;
    }

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
