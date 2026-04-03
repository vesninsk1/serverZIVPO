package com.example.server.services;

import com.example.server.entities.Device;
import com.example.server.entities.License;
import com.example.server.models.Ticket;
import com.example.server.models.TicketResponse;
import com.example.server.repositories.DeviceLicenseRepository;
import com.example.server.repositories.DeviceRepository;
import com.example.server.repositories.LicenseRepository;
import com.example.server.signature.SigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final LicenseRepository licenseRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final SigningService signingService;

    @Value("${ticket.time-to-live:300}")
    private Long defaultTimeToLive;

    public TicketResponse generateTicket(String activationKey, String macAddress, Long userId) {
        License license = licenseRepository.findByCode(activationKey)
                .orElseThrow(() -> new RuntimeException("License not found"));

        Device device = deviceRepository.findByMacAddress(macAddress)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        if (!device.getUserId().equals(userId)) {
            throw new RuntimeException("Device does not belong to this user");
        }

        boolean isLicenseActivatedOnDevice = deviceLicenseRepository
                .existsByLicenseIdAndDeviceId(license.getId(), device.getId());

        if (!isLicenseActivatedOnDevice) {
            throw new RuntimeException("License is not activated on this device");
        }

        Ticket ticket = Ticket.builder()
                .serverTime(LocalDateTime.now())
                .timeToLive(defaultTimeToLive)
                .activationDate(license.getFirstActivationDate() != null 
                        ? license.getFirstActivationDate().atStartOfDay() 
                        : null)
                .expirationDate(license.getEndingDate() != null 
                        ? license.getEndingDate().atStartOfDay() 
                        : null)
                .userId(userId)
                .deviceId(device.getId())
                .blocked(license.getBlocked())
                .build();

        String signature = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("ticket", ticket);
            payload.put("timestamp", System.currentTimeMillis());
            signature = signingService.sign(payload);
        } catch (Exception e) {
            log.warn("Failed to sign ticket: {}", e.getMessage());
        }

        return TicketResponse.builder()
                .ticket(ticket)
                .signature(signature)
                .build();
    }

    public TicketResponse generateTicketByLicenseId(Long licenseId, Long deviceId, Long userId) {
        License license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new RuntimeException("License not found"));

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        if (!device.getUserId().equals(userId)) {
            throw new RuntimeException("Device does not belong to this user");
        }

        boolean isLicenseActivatedOnDevice = deviceLicenseRepository
                .existsByLicenseIdAndDeviceId(license.getId(), device.getId());

        if (!isLicenseActivatedOnDevice) {
            throw new RuntimeException("License is not activated on this device");
        }

        Ticket ticket = Ticket.builder()
                .serverTime(LocalDateTime.now())
                .timeToLive(defaultTimeToLive)
                .activationDate(license.getFirstActivationDate() != null 
                        ? license.getFirstActivationDate().atStartOfDay() 
                        : null)
                .expirationDate(license.getEndingDate() != null 
                        ? license.getEndingDate().atStartOfDay() 
                        : null)
                .userId(userId)
                .deviceId(device.getId())
                .blocked(license.getBlocked())
                .build();

        String signature = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("ticket", ticket);
            payload.put("timestamp", System.currentTimeMillis());
            signature = signingService.sign(payload);
        } catch (Exception e) {
            log.warn("Failed to sign ticket: {}", e.getMessage());
        }

        return TicketResponse.builder()
                .ticket(ticket)
                .signature(signature)
                .build();
    }
}