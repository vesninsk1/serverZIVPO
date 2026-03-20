package com.example.server.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "mac_address", nullable = false, unique = true)
    private String macAddress;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
}