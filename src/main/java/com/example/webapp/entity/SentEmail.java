package com.example.webapp.entity;

import jakarta.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "sent_emails")
public class SentEmail {

    @Id
    @Column(nullable = false, unique = true)
    private String token; // Token as the primary key

    @Column(nullable = false)
    private String email;

    @Column(name = "sent_at", nullable = false)
    private Timestamp sentAt;

    @Column(nullable = false)
    private Timestamp expiry; // Expiry column added

    @Column(length = 50)
    private String status;

    // Getters and Setters

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Timestamp getSentAt() {
        return sentAt;
    }

    public void setSentAt(Timestamp sentAt) {
        this.sentAt = sentAt;
    }

    public Timestamp getExpiry() {
        return expiry;
    }

    public void setExpiry(Timestamp expiry) {
        this.expiry = expiry;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
