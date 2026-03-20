package com.example.server.entities;

import com.example.server.models.LicenseEventStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "license_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenseHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "license_id", nullable = false)
    private Long licenseId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LicenseEventStatus status;
    
    @Column(name = "change_date", nullable = false)
    @Builder.Default
    private LocalDateTime changeDate = LocalDateTime.now();
    
    @Column(length = 1000)
    private String description;
}