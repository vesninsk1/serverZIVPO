package com.example.server.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LicenseTicket {
    private Long licenseId;
    private String code;
    private String productName;
    private String licenseType;
    private String ownerEmail;
    private String userEmail;
    private LocalDate firstActivationDate;
    private LocalDate endingDate;
    private Boolean blocked;
    private Integer deviceCount;
    private Integer activatedDevicesCount;
    private List<DeviceInfo> devices;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceInfo {
        private Long deviceId;
        private String deviceName;
        private String macAddress;
        private LocalDate activationDate;
    }
}