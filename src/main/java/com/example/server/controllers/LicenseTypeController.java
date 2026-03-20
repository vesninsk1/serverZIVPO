package com.example.server.controllers;

import com.example.server.entities.LicenseType;
import com.example.server.repositories.LicenseTypeRepository;
import com.example.server.services.LicenseTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/license-types")
@RequiredArgsConstructor
public class LicenseTypeController {
    
    private final LicenseTypeService licenseTypeService;
    private final LicenseTypeRepository licenseTypeRepository;
    
    @GetMapping
    public List<LicenseType> getAllLicenseTypes() {
        return licenseTypeRepository.findAll();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<LicenseType> getLicenseType(@PathVariable Long id) {
        try {
            LicenseType licenseType = licenseTypeService.getTypeOrFail(id);
            return ResponseEntity.ok(licenseType);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    @PostMapping
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<?> createLicenseType(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            Integer duration = (Integer) request.get("defaultDurationInDays");
            String description = (String) request.get("description");
            
            LicenseType licenseType = licenseTypeService.createLicenseType(name, duration, description);
            return ResponseEntity.ok(licenseType);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}