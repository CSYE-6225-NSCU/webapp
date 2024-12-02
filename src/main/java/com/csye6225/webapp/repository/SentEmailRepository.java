package com.csye6225.webapp.repository;

import com.csye6225.webapp.model.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
    Optional<SentEmail> findByToken(String token);
}
