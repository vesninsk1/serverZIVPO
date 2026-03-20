package com.example.server.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "licenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class License {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    
    @Column(name = "product_id", nullable = false)
    private Long productId;
    
    @Column(name = "type_id", nullable = false)
    private Long typeId;
    
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "first_activation_date")
    private LocalDate firstActivationDate;
    
    @Column(name = "ending_date")
    private LocalDate endingDate;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean blocked = false;
    
    @Column(name = "device_count", nullable = false)
    @Builder.Default
    private Integer deviceCount = 1;
    
    @Column(length = 500)
    private String description;
}