package com.example.server.controllers;

import com.example.server.entities.License;
import com.example.server.entities.LicenseHistory;
import com.example.server.entities.User;
import com.example.server.models.*;
import com.example.server.repositories.LicenseHistoryRepository;
import com.example.server.repositories.LicenseRepository;
import com.example.server.services.DeviceService;
import com.example.server.services.LicenseService;
import com.example.server.services.LicenseTicketBuilder;
import com.example.server.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/licenses")
@RequiredArgsConstructor
public class LicenseController {
    
    private final LicenseService licenseService;
    private final LicenseRepository licenseRepository;
    private final LicenseHistoryRepository licenseHistoryRepository;
    private final LicenseTicketBuilder ticketBuilder;
    private final DeviceService deviceService;
    private final TicketService ticketService;
    
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<?> createLicense(
            @RequestBody CreateLicenseRequest request,
            @AuthenticationPrincipal User admin) {
        try {
            Long adminId = admin.getId();
            License license = licenseService.createLicense(request, adminId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ticketBuilder.buildTicket(license));
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
        }
    }
    
    @PostMapping("/activate")
    public ResponseEntity<?> activateLicense(
            @RequestBody ActivateLicenseRequest request,
            @AuthenticationPrincipal User user) {
        try {
            Long userId = user.getId();
            LicenseTicket ticket = licenseService.activateLicense(request, userId);
            return ResponseEntity.ok(ticket);
        } catch (RuntimeException e) {
            String message = e.getMessage();

            if (message.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            if (message.contains("another user")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", message));
            }
            if (message.contains("limit reached")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", message));
            }
            if (message.contains("blocked")) {
                return ResponseEntity.status(423)
                        .body(Map.of("error", message));
            }

            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
        }
    }
    
    @PostMapping("/renew")
    public ResponseEntity<?> renewLicense(
            @RequestBody RenewLicenseRequest request,
            @AuthenticationPrincipal User user) {
        try {
            Long userId = user.getId();
            LicenseTicket ticket = licenseService.renewLicense(request, userId);
            return ResponseEntity.ok(ticket);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            
            if (message.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            if (message.contains("belong") || message.contains("another user")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", message));
            }
            if (message.contains("eligible") || message.contains("renewal")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", message));
            }
            if (message.contains("blocked")) {
                return ResponseEntity.status(423)
                        .body(Map.of("error", message));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
        }
    }
    
    @PostMapping("/check")
    public ResponseEntity<?> checkLicense(
            @RequestBody CheckLicenseRequest request,
            @AuthenticationPrincipal User user) {
        try {
            Long userId = user.getId();
            
            if (!deviceService.deviceExistsAndBelongsToUser(request.getDeviceMac(), userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found or does not belong to user. Please register the device first."));
            }
            
            LicenseTicket ticket = licenseService.checkLicense(request, userId);
            return ResponseEntity.ok(ticket);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            
            if (message.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
        }
    }
    
    @PostMapping("/{licenseCode}/ticket")
    public ResponseEntity<?> getLicenseTicket(
            @PathVariable String licenseCode,
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal User user) {
        try {
            String macAddress = request.get("macAddress");
            if (macAddress == null || macAddress.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "MAC address is required"));
            }
            
            TicketResponse response = ticketService.generateTicket(
                    licenseCode,
                    macAddress,
                    user.getId()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            
            if (message.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            if (message.contains("not belong") || message.contains("not activated")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", message));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
        }
    }
    
    @GetMapping("/{code}")
    public ResponseEntity<?> getLicenseByCode(@PathVariable String code) {
        try {
            License license = licenseRepository.findByCode(code)
                    .orElseThrow(() -> new RuntimeException("License not found"));
            return ResponseEntity.ok(ticketBuilder.buildTicket(license));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/user")
    public ResponseEntity<?> getUserLicenses(@AuthenticationPrincipal User user) {
        try {
            Long userId = user.getId();
            List<License> licenses = licenseRepository.findByUserId(userId);
            List<LicenseTicket> tickets = licenses.stream()
                    .map(ticketBuilder::buildTicket)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(tickets);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/user/active")
    public ResponseEntity<?> getUserActiveLicenses(
            @RequestParam Long productId,
            @AuthenticationPrincipal User user) {
        try {
            Long userId = user.getId();
            List<License> licenses = licenseRepository.findActiveByUserAndProduct(userId, productId);
            List<LicenseTicket> tickets = licenses.stream()
                    .map(ticketBuilder::buildTicket)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(tickets);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/{licenseId}/history")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<?> getLicenseHistory(@PathVariable Long licenseId) {
        try {
            List<LicenseHistory> history = licenseHistoryRepository.findByLicenseIdOrderByChangeDateDesc(licenseId);
            return ResponseEntity.ok(history);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{licenseId}/block")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<?> blockLicense(
            @PathVariable Long licenseId,
            @RequestParam boolean blocked,
            @AuthenticationPrincipal User admin) {
        try {
            Long adminId = admin.getId();
            licenseService.blockLicense(licenseId, adminId, blocked);
            return ResponseEntity.ok(Map.of(
                    "message", "License " + (blocked ? "blocked" : "unblocked") + " successfully"
            ));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}