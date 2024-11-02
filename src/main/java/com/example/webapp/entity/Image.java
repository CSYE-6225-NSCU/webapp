package com.example.webapp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "images")
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JsonProperty("image_id")
    private UUID imageId;

    @Column(nullable = false)
    @JsonProperty("file_name")
    private String fileName;

    @Column(nullable = false)
    @JsonProperty("url")
    private String url;

    @Column(nullable = false)
    @JsonProperty("upload_date")
    private LocalDateTime uploadDate;

    @OneToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private User user;

    // Constructors
    public Image() {
    }

    public Image(String fileName, String url, LocalDateTime uploadDate) {
        this.fileName = fileName;
        this.url = url;
        this.uploadDate = uploadDate;
    }

    // Getters and Setters

    public UUID getImageId() {
        return imageId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
