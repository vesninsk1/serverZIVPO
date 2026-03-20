package com.example.server.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "license_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenseType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(name = "default_duration_in_days", nullable = false)
    private Integer defaultDurationInDays;
    
    @Column(length = 500)
    private String description;
}