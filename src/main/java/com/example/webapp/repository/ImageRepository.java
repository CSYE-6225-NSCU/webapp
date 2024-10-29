package com.example.webapp.repository;

import com.example.webapp.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {
    Image findByUserId(Long userId);
}
