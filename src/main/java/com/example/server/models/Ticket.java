package com.example.server.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    private LocalDateTime serverTime;
    private Long timeToLive;
    private LocalDateTime activationDate;
    private LocalDateTime expirationDate;
    private Long userId;
    private Long deviceId;
    private Boolean blocked;
}