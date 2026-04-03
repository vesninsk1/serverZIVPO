package com.example.server.repositories;

import com.example.server.entities.Signature;
import com.example.server.models.SignatureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SignatureRepository extends JpaRepository<Signature, UUID> {
    Optional<Signature> findByThreatName(String threatName);
    List<Signature> findByStatus(SignatureStatus status);
    List<Signature> findByStatusNot(SignatureStatus status);
    List<Signature> findByUpdatedAtAfter(Instant since);
    boolean existsByThreatName(String threatName);
}