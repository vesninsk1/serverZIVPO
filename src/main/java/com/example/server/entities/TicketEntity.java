package com.example.server.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "server_time", nullable = false)
    private LocalDateTime serverTime;

    @Column(name = "time_to_live", nullable = false)
    private Long timeToLive;

    @Column(name = "activation_date")
    private LocalDateTime activationDate;
    
    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "license_id", nullable = false)
    private Long licenseId;

    @Column(nullable = false)
    private Boolean blocked;
}