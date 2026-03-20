package com.example.server.services;

import com.example.server.entities.*;
import com.example.server.models.*;
import com.example.server.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LicenseService {
    
    private final LicenseRepository licenseRepository;
    private final LicenseTypeService licenseTypeService;
    private final ProductService productService;
    private final UserDetailsServiceImpl userDetailsService;
    private final DeviceService deviceService;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final LicenseHistoryRepository licenseHistoryRepository;
    private final LicenseKeyGenerator licenseKeyGenerator;
    private final LicenseTicketBuilder ticketBuilder;
    
    @Transactional
    public License createLicense(CreateLicenseRequest request, Long adminId) {
        Product product = productService.getProductOrFail(request.getProductId());
        if (product.getIsBlocked()) {
            throw new RuntimeException("Cannot create license for blocked product");
        }
 
        LicenseType licenseType = licenseTypeService.getTypeOrFail(request.getTypeId());
        User owner = (User) userDetailsService.loadUserById(request.getOwnerId());
        String code;
        do {
            code = licenseKeyGenerator.generateCode();
        } while (licenseRepository.findByCode(code).isPresent());
        
        License license = License.builder()
                .code(code)
                .productId(product.getId())
                .typeId(licenseType.getId())
                .ownerId(owner.getId())
                .blocked(false)
                .deviceCount(request.getDeviceCount() != null ? request.getDeviceCount() : 1)
                .description(request.getDescription())
                .build();
        
        License savedLicense = licenseRepository.save(license);
        
        LicenseHistory history = LicenseHistory.builder()
                .licenseId(savedLicense.getId())
                .userId(adminId)
                .status(LicenseEventStatus.CREATED)
                .description("License created for product: " + product.getName())
                .build();
        licenseHistoryRepository.save(history);
        
        log.info("License created: {} by admin: {}", code, adminId);
        return savedLicense;
    }
    
    
    @Transactional
    public LicenseTicket activateLicense(ActivateLicenseRequest request, Long userId) {

        License license = licenseRepository.findByCode(request.getActivationKey())
                .orElseThrow(() -> new RuntimeException("License not found with code: " + request.getActivationKey()));

        if (license.getBlocked()) {
            throw new RuntimeException("License is blocked");
        }
        
        Product product = productService.getProductOrFail(license.getProductId());
        if (product.getIsBlocked()) {
            throw new RuntimeException("Product is blocked");
        }
        
        if (license.getUserId() != null && !license.getUserId().equals(userId)) {
            throw new RuntimeException("License already activated by another user");
        }

        Device device = deviceService.getDeviceByMacAndUserOrFail(request.getDeviceMac(), userId);

        if (deviceLicenseRepository.existsByLicenseIdAndDeviceId(license.getId(), device.getId())) {
            throw new RuntimeException("License already activated on this device");
        }
        
        boolean isFirstActivation = license.getUserId() == null;
        
        if (isFirstActivation) {
            LicenseType licenseType = licenseTypeService.getTypeOrFail(license.getTypeId());
            
            license.setUserId(userId);
            license.setFirstActivationDate(LocalDate.now());
            license.setEndingDate(LocalDate.now().plusDays(licenseType.getDefaultDurationInDays()));
            
            licenseRepository.save(license);
        } else {
            int activatedCount = deviceLicenseRepository.countByLicenseId(license.getId());
            if (activatedCount >= license.getDeviceCount()) {
                throw new RuntimeException("Device limit reached for this license. Maximum devices: " + license.getDeviceCount());
            }
        }
        
        DeviceLicense deviceLicense = DeviceLicense.builder()
                .licenseId(license.getId())
                .deviceId(device.getId())
                .activationDate(LocalDateTime.now())
                .build();
        deviceLicenseRepository.save(deviceLicense);

        LicenseHistory history = LicenseHistory.builder()
                .licenseId(license.getId())
                .userId(userId)
                .status(LicenseEventStatus.ACTIVATED)
                .description("License activated on device: " + device.getName() + 
                           " (" + device.getMacAddress() + ")" +
                           (isFirstActivation ? " - First activation" : ""))
                .build();
        licenseHistoryRepository.save(history);
        
        log.info("License activated: {} by user: {} on device: {}", 
                license.getCode(), userId, device.getMacAddress());
        
        return ticketBuilder.buildTicket(license);
    }
    
    @Transactional
    public LicenseTicket renewLicense(RenewLicenseRequest request, Long userId) {
        License license = licenseRepository.findByCode(request.getActivationKey())
                .orElseThrow(() -> new RuntimeException("License not found with code: " + request.getActivationKey()));
 
        if (!license.getUserId().equals(userId)) {
            throw new RuntimeException("License does not belong to this user");
        }
        
        if (license.getBlocked()) {
            throw new RuntimeException("License is blocked");
        }
        
        LocalDate now = LocalDate.now();
        LocalDate endingDate = license.getEndingDate();
        boolean isExpiredOrExpiringSoon = endingDate == null || 
                                          endingDate.isBefore(now) || 
                                          endingDate.minusDays(7).isBefore(now);
        
        if (!isExpiredOrExpiringSoon) {
            throw new RuntimeException("License is not eligible for renewal. Renewal is allowed only when expired or within 7 days before expiration.");
        }
        
        LicenseType licenseType = licenseTypeService.getTypeOrFail(license.getTypeId());

        LocalDate newEndingDate;
        if (license.getEndingDate() == null || license.getEndingDate().isBefore(now)) {
            newEndingDate = now.plusDays(licenseType.getDefaultDurationInDays());
        } else {
            newEndingDate = license.getEndingDate().plusDays(licenseType.getDefaultDurationInDays());
        }
        
        license.setEndingDate(newEndingDate);
        licenseRepository.save(license);

        LicenseHistory history = LicenseHistory.builder()
                .licenseId(license.getId())
                .userId(userId)
                .status(LicenseEventStatus.RENEWED)
                .description("License renewed until: " + newEndingDate)
                .build();
        licenseHistoryRepository.save(history);
        
        log.info("License renewed: {} by user: {} until: {}", 
                license.getCode(), userId, newEndingDate);
        
        return ticketBuilder.buildTicket(license);
    }
    
    @Transactional(readOnly = true)
    public LicenseTicket checkLicense(CheckLicenseRequest request, Long userId) {
        Device device = deviceService.getDeviceByMacAndUserOrFail(request.getDeviceMac(), userId);

        License license = licenseRepository.findActiveByDeviceUserAndProduct(
                request.getDeviceMac(), userId, request.getProductId())
                .orElseThrow(() -> new RuntimeException("No active license found for this device and product"));
        
        return ticketBuilder.buildTicket(license);
    }
    
    @Transactional
    public void blockLicense(Long licenseId, Long adminId, boolean blocked) {
        License license = licenseRepository.findById(licenseId)
                .orElseThrow(() -> new RuntimeException("License not found with id: " + licenseId));
        
        license.setBlocked(blocked);
        licenseRepository.save(license);
        
        LicenseEventStatus status = blocked ? LicenseEventStatus.BLOCKED : LicenseEventStatus.UNBLOCKED;
        
        LicenseHistory history = LicenseHistory.builder()
                .licenseId(licenseId)
                .userId(adminId)
                .status(status)
                .description("License " + (blocked ? "blocked" : "unblocked"))
                .build();
        licenseHistoryRepository.save(history);
        
        log.info("License {}: {} by admin: {}", 
                blocked ? "blocked" : "unblocked", license.getCode(), adminId);
    }
    
    @Transactional
    public void expireOldLicenses() {
        LocalDate now = LocalDate.now();
        java.util.List<License> expiredLicenses = licenseRepository.findByEndingDateBeforeAndBlockedFalse(now);
        
        for (License license : expiredLicenses) {
            LicenseHistory history = LicenseHistory.builder()
                    .licenseId(license.getId())
                    .userId(license.getUserId())
                    .status(LicenseEventStatus.EXPIRED)
                    .description("License expired on: " + license.getEndingDate())
                    .build();
            licenseHistoryRepository.save(history);
            
            log.info("License expired: {}", license.getCode());
        }
    }
}