package com.example.server.repositories;

import com.example.server.entities.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TicketRepository extends JpaRepository<TicketEntity, Long> {
    
    List<TicketEntity> findByUserId(Long userId);
    
    List<TicketEntity> findByDeviceId(Long deviceId);
     
    @Query("SELECT t FROM TicketEntity t WHERE t.licenseId = :licenseId AND t.deviceId = :deviceId ORDER BY t.serverTime DESC")
    List<TicketEntity> findByLicenseIdAndDeviceId(@Param("licenseId") Long licenseId, @Param("deviceId") Long deviceId);
}