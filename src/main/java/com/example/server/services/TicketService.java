package com.example.server.services;

import com.example.server.entities.Device;
import com.example.server.entities.License;
import com.example.server.entities.TicketEntity;
import com.example.server.models.Ticket;
import com.example.server.models.TicketResponse;
import com.example.server.repositories.DeviceLicenseRepository;
import com.example.server.repositories.DeviceRepository;
import com.example.server.repositories.LicenseRepository;
import com.example.server.repositories.TicketRepository;
import com.example.server.signature.SigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final LicenseRepository licenseRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final TicketRepository ticketRepository;
    private final SigningService signingService;

    @Value("${ticket.time-to-live:300}")
    private Long defaultTimeToLive;

    @Transactional
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

        LocalDateTime now = LocalDateTime.now();
        
        Ticket ticket = Ticket.builder()
                .serverTime(now)
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

        TicketEntity ticketEntity = TicketEntity.builder()
                .serverTime(now)
                .timeToLive(defaultTimeToLive)
                .activationDate(ticket.getActivationDate())
                .expirationDate(ticket.getExpirationDate())
                .userId(userId)
                .deviceId(device.getId())
                .licenseId(license.getId())
                .blocked(license.getBlocked())
                .build();
        
        ticketRepository.save(ticketEntity);
        log.info("Ticket generated and saved: {} for user: {}, device: {}", userId, device.getId());

        String signature = buildSignatureForTicket(ticket);

        return TicketResponse.builder()
                .ticket(ticket)
                .electronicDigitalSignature(signature)
                .build();
    }

    @Transactional(readOnly = true)
    public TicketResponse buildTicketResponseFromEntity(TicketEntity ticketEntity) {
        Ticket ticket = convertToTicket(ticketEntity);
        String signature = buildSignatureForTicket(ticket);
        
        return TicketResponse.builder()
                .ticket(ticket)
                .electronicDigitalSignature(signature)
                .build();
    }
    private String buildSignatureForTicket(Ticket ticket) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("serverTime", ticket.getServerTime());
            payload.put("activationDate", ticket.getActivationDate());
            payload.put("expirationDate", ticket.getExpirationDate());
            payload.put("userId", ticket.getUserId());
            payload.put("deviceId", ticket.getDeviceId());
            payload.put("blocked", ticket.getBlocked());
            payload.put("timestamp", System.currentTimeMillis());
            return signingService.sign(payload);
        } catch (Exception e) {
            log.error("Failed to sign ticket: {}", e.getMessage(), e);
            return null;
        }
    }
    private Ticket convertToTicket(TicketEntity entity) {
        return Ticket.builder()
                .serverTime(entity.getServerTime())
                .timeToLive(entity.getTimeToLive())
                .activationDate(entity.getActivationDate())
                .expirationDate(entity.getExpirationDate())
                .userId(entity.getUserId())
                .deviceId(entity.getDeviceId())
                .blocked(entity.getBlocked())
                .build();
    }
}