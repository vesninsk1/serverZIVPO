package com.example.server.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivateLicenseRequest {
    private String activationKey;
    private String deviceMac;
    private String deviceName;
}