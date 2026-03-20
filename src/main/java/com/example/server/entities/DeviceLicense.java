package com.example.server.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_licenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceLicense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "license_id", nullable = false)
    private Long licenseId;
    
    @Column(name = "device_id", nullable = false)
    private Long deviceId;
    
    @Column(name = "activation_date", nullable = false)
    @Builder.Default
    private LocalDateTime activationDate = LocalDateTime.now();
}