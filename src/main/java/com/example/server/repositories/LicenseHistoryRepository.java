package com.example.server.repositories;

import com.example.server.entities.LicenseHistory;
import com.example.server.models.LicenseEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LicenseHistoryRepository extends JpaRepository<LicenseHistory, Long> {
    List<LicenseHistory> findByLicenseIdOrderByChangeDateDesc(Long licenseId);
    List<LicenseHistory> findByUserIdOrderByChangeDateDesc(Long userId);
    List<LicenseHistory> findByStatus(LicenseEventStatus status);
}