package com.example.server.repositories;

import com.example.server.entities.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByMacAddress(String macAddress);
    List<Device> findByUserId(Long userId);
    boolean existsByMacAddress(String macAddress);
    
    @Query("SELECT d FROM Device d WHERE d.userId = :userId AND d.macAddress = :macAddress")
    Optional<Device> findByUserIdAndMacAddress(@Param("userId") Long userId, @Param("macAddress") String macAddress);
}