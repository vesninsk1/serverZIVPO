package com.example.server.repositories;

import com.example.server.entities.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LicenseRepository extends JpaRepository<License, Long> {
    Optional<License> findByCode(String code);
    List<License> findByOwnerId(Long ownerId);
    List<License> findByUserId(Long userId);
    List<License> findByProductId(Long productId);
    
    @Query("SELECT l FROM License l WHERE l.userId = :userId AND l.productId = :productId " +
           "AND l.blocked = false AND l.endingDate >= CURRENT_DATE")
    List<License> findActiveByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);
    
    @Query("SELECT l FROM License l " +
           "JOIN DeviceLicense dl ON l.id = dl.licenseId " +
           "JOIN Device d ON dl.deviceId = d.id " +
           "WHERE d.macAddress = :macAddress AND l.userId = :userId " +
           "AND l.productId = :productId AND l.blocked = false " +
           "AND l.endingDate >= CURRENT_DATE")
    Optional<License> findActiveByDeviceUserAndProduct(
            @Param("macAddress") String macAddress,
            @Param("userId") Long userId,
            @Param("productId") Long productId);
    
    @Query("SELECT COUNT(dl) FROM DeviceLicense dl WHERE dl.licenseId = :licenseId")
    Integer countActivatedDevices(@Param("licenseId") Long licenseId);
    
    @Query("SELECT l FROM License l WHERE l.endingDate < CURRENT_DATE AND l.blocked = false")
    List<License> findExpiredLicenses();
    
    @Query("SELECT l FROM License l WHERE l.endingDate <= :date AND l.endingDate > CURRENT_DATE")
    List<License> findLicensesExpiringBefore(@Param("date") LocalDate date);
    
    // Добавляем недостающий метод
    @Query("SELECT l FROM License l WHERE l.endingDate < :date AND l.blocked = false")
    List<License> findByEndingDateBeforeAndBlockedFalse(@Param("date") LocalDate date);
}