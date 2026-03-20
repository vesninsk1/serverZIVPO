package com.example.server.services;

import com.example.server.entities.*;
import com.example.server.models.LicenseTicket;
import com.example.server.models.Ticket;
import com.example.server.repositories.DeviceLicenseRepository;
import com.example.server.repositories.DeviceRepository;
import com.example.server.repositories.ProductRepository;
import com.example.server.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LicenseTicketBuilder {
    
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final LicenseTypeService licenseTypeService;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final DeviceRepository deviceRepository;
    
    public LicenseTicket buildTicket(License license) {
        LicenseTicket ticket = new LicenseTicket();
        ticket.setLicenseId(license.getId());
        ticket.setCode(license.getCode());
        ticket.setBlocked(license.getBlocked());
        ticket.setFirstActivationDate(license.getFirstActivationDate());
        ticket.setEndingDate(license.getEndingDate());
        ticket.setDeviceCount(license.getDeviceCount());
        
        Product product = productRepository.findById(license.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        ticket.setProductName(product.getName());
        
        LicenseType licenseType = licenseTypeService.getTypeOrFail(license.getTypeId());
        ticket.setLicenseType(licenseType.getName());
        
        userRepository.findById(license.getOwnerId())
                .ifPresent(owner -> ticket.setOwnerEmail(owner.getEmail()));
        
        if (license.getUserId() != null) {
            userRepository.findById(license.getUserId())
                    .ifPresent(user -> ticket.setUserEmail(user.getEmail()));
        }

        int activatedCount = deviceLicenseRepository.countByLicenseId(license.getId());
        ticket.setActivatedDevicesCount(activatedCount);

        var deviceLicenses = deviceLicenseRepository.findByLicenseId(license.getId());
        ticket.setDevices(deviceLicenses.stream()
                .map(dl -> {
                    Device device = deviceRepository.findById(dl.getDeviceId())
                            .orElseThrow(() -> new RuntimeException("Device not found"));
                    return new LicenseTicket.DeviceInfo(
                            device.getId(),
                            device.getName(),
                            device.getMacAddress(),
                            dl.getActivationDate().toLocalDate()
                    );
                })
                .collect(Collectors.toList()));
        
        return ticket;
    }
    
    public Ticket buildSimpleTicket(License license, Device device) {
        return Ticket.builder()
                .activationDate(license.getFirstActivationDate() != null 
                        ? license.getFirstActivationDate().atStartOfDay() 
                        : null)
                .expirationDate(license.getEndingDate() != null 
                        ? license.getEndingDate().atStartOfDay() 
                        : null)
                .userId(license.getUserId())
                .deviceId(device.getId())
                .blocked(license.getBlocked())
                .build();
    }
}