package com.example.server.controllers;

import com.example.server.entities.License;
import com.example.server.entities.LicenseHistory;
import com.example.server.entities.User;
import com.example.server.models.*;
import com.example.server.repositories.LicenseHistoryRepository;
import com.example.server.services.LicenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/licenses")
@RequiredArgsConstructor
public class LicenseController {
    
    private final LicenseService licenseService;
    private final LicenseHistoryRepository licenseHistoryRepository;
    @PostMapping("/create")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<?> createLicense(
            @RequestBody CreateLicenseRequest request,
            @AuthenticationPrincipal User admin) {
        try {
            Long adminId = admin.getId();
            License license = licenseService.createLicense(request, adminId);
            return ResponseEntity.status(HttpStatus.CREATED).body(license);
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
            TicketResponse response = licenseService.activateLicense(request, userId);
            return ResponseEntity.ok(response);
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
            TicketResponse response = licenseService.renewLicense(request, userId);
            return ResponseEntity.ok(response);
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
            
            TicketResponse response = licenseService.checkLicense(request, userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            
            if (message.contains("Device not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found. Please register the device first."));
            }
            if (message.contains("does not belong")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", message));
            }
            if (message.contains("No active license found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            if (message.contains("No valid ticket found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No valid ticket found. Please activate the license first."));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
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