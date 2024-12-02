package com.csye6225.webapp.dto;

import java.time.LocalDate;

public class ProfilePicResponseDto {
    private String fileName;
    private String id;
    private String url;
    private LocalDate uploadDate;
    private String userId;

    // Constructors, Getters, and Setters
    public ProfilePicResponseDto(String fileName, String id, String url, LocalDate uploadDate, String userId) {
        this.fileName = fileName;
        this.id = id;
        this.url = url;
        this.uploadDate = uploadDate;
        this.userId = userId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public LocalDate getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDate uploadDate) {
        this.uploadDate = uploadDate;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
