package com.example.server.repositories;

import com.example.server.entities.SignatureAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SignatureAuditRepository extends JpaRepository<SignatureAudit, Long> {
    List<SignatureAudit> findBySignatureIdOrderByChangedAtDesc(UUID signatureId);
    List<SignatureAudit> findByChangedBy(String changedBy);
}