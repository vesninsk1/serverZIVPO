package com.example.server.services;

import com.example.server.entities.LicenseType;
import com.example.server.repositories.LicenseTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LicenseTypeService {
    
    private final LicenseTypeRepository licenseTypeRepository;
    
    @Transactional(readOnly = true)
    public LicenseType getTypeOrFail(Long typeId) {
        return licenseTypeRepository.findById(typeId)
                .orElseThrow(() -> new RuntimeException("License type not found with id: " + typeId));
    }
    
    @Transactional
    public LicenseType createLicenseType(String name, Integer duration, String description) {
        if (licenseTypeRepository.existsByName(name)) {
            throw new RuntimeException("License type with name '" + name + "' already exists");
        }
        
        LicenseType licenseType = LicenseType.builder()
                .name(name)
                .defaultDurationInDays(duration)
                .description(description)
                .build();
        
        return licenseTypeRepository.save(licenseType);
    }
}