package com.example.webapp.repository;

import com.example.webapp.entity.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
    Optional<SentEmail> findByToken(String token);
}
