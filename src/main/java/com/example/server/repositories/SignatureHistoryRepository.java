package com.example.server.repositories;

import com.example.server.entities.SignatureHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SignatureHistoryRepository extends JpaRepository<SignatureHistory, Long> {
    List<SignatureHistory> findBySignatureIdOrderByVersionCreatedAtDesc(UUID signatureId);
    
    @Query("SELECT h FROM SignatureHistory h WHERE h.signatureId = :signatureId ORDER BY h.versionCreatedAt DESC")
    Optional<SignatureHistory> findTopBySignatureIdOrderByVersionCreatedAtDesc(@Param("signatureId") UUID signatureId);
}