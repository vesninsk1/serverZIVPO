package com.example.server.controllers;

import com.example.server.entities.Device;
import com.example.server.entities.License;
import com.example.server.entities.User;
import com.example.server.models.DeviceRegisterRequest;
import com.example.server.models.DeviceUpdateRequest;
import com.example.server.models.LicenseTicket;
import com.example.server.models.TicketResponse;
import com.example.server.repositories.DeviceLicenseRepository;
import com.example.server.repositories.DeviceRepository;
import com.example.server.repositories.LicenseRepository;
import com.example.server.services.DeviceService;
import com.example.server.services.LicenseTicketBuilder;
import com.example.server.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final LicenseRepository licenseRepository;
    private final DeviceService deviceService;
    private final LicenseTicketBuilder ticketBuilder;
    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<?> getUserDevices(@AuthenticationPrincipal User user) {
        try {
            Long userId = user.getId();
            List<Device> devices = deviceService.getUserDevices(userId);
            return ResponseEntity.ok(devices);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<?> getDeviceById(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal User user) {
        try {
            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new RuntimeException("Device not found"));
            
            if (!device.getUserId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }
            
            return ResponseEntity.ok(device);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerDevice(
            @RequestBody DeviceRegisterRequest request,
            @AuthenticationPrincipal User user) {
        try {
            Long userId = user.getId();
            
            if (request.getMacAddress() == null || request.getMacAddress().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "MAC address is required"));
            }
            
            String macPattern = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";
            if (!request.getMacAddress().matches(macPattern)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid MAC address format. Expected format: XX:XX:XX:XX:XX:XX"));
            }
            
            Device device = deviceService.registerDevice(
                    request.getMacAddress().toUpperCase(), 
                    request.getName(), 
                    userId);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(device);
            
        } catch (RuntimeException e) {
            String message = e.getMessage();
            
            if (message.contains("already registered to another user")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", message));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
        }
    }

    @PutMapping("/{deviceId}")
    public ResponseEntity<?> updateDevice(
            @PathVariable Long deviceId,
            @RequestBody DeviceUpdateRequest request,
            @AuthenticationPrincipal User user) {
        try {
            Device device = deviceService.updateDeviceName(deviceId, request.getName(), user.getId());
            return ResponseEntity.ok(device);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            
            if (message.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            if (message.contains("not belong")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", message));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
        }
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<?> deleteDevice(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal User user) {
        try {
            List<com.example.server.entities.DeviceLicense> deviceLicenses = 
                    deviceLicenseRepository.findByDeviceId(deviceId);
            
            if (!deviceLicenses.isEmpty()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Cannot delete device with active licenses. Deactivate licenses first."));
            }
            
            deviceService.deleteDevice(deviceId, user.getId());
            return ResponseEntity.ok(Map.of("message", "Device deleted successfully"));
        } catch (RuntimeException e) {
            String message = e.getMessage();
            
            if (message.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", message));
            }
            if (message.contains("not belong")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", message));
            }
            return ResponseEntity.badRequest()
                    .body(Map.of("error", message));
        }
    }

    @GetMapping("/{deviceId}/licenses")
    public ResponseEntity<?> getDeviceLicenses(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal User user) {
        try {
            Device device = deviceRepository.findById(deviceId)
                    .orElseThrow(() -> new RuntimeException("Device not found"));
            
            if (!device.getUserId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Access denied"));
            }
            
            List<com.example.server.entities.DeviceLicense> deviceLicenses = 
                    deviceLicenseRepository.findByDeviceId(deviceId);
            
            List<LicenseTicket> tickets = deviceLicenses.stream()
                    .map(dl -> licenseRepository.findById(dl.getLicenseId()))
                    .filter(java.util.Optional::isPresent)
                    .map(opt -> ticketBuilder.buildTicket(opt.get()))
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(tickets);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/check-license")
    public ResponseEntity<?> checkDeviceLicense(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal User user) {
        try {
            String macAddress = (String) request.get("macAddress");
            Long productId = ((Number) request.get("productId")).longValue();
            Long userId = user.getId();
            
            if (macAddress == null || macAddress.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "MAC address is required"));
            }
            
            if (!deviceService.deviceExistsAndBelongsToUser(macAddress, userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Device not found or does not belong to user. Please register the device first."));
            }
            
            java.util.Optional<License> licenseOpt = licenseRepository.findActiveByDeviceUserAndProduct(
                    macAddress, userId, productId);
            
            if (licenseOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No active license found for this device and product"));
            }
            
            TicketResponse ticketResponse = ticketService.generateTicket(
                    licenseOpt.get().getCode(),
                    macAddress,
                    userId
            );
            
            return ResponseEntity.ok(ticketResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/{deviceId}/licenses/{licenseId}/ticket")
    public ResponseEntity<?> getDeviceLicenseTicket(
            @PathVariable Long deviceId,
            @PathVariable Long licenseId,
            @AuthenticationPrincipal User user) {
        try {
            TicketResponse response = ticketService.generateTicketByLicenseId(
                    licenseId,
                    deviceId,
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
}