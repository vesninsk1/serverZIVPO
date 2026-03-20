package com.example.server.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLicenseRequest {
    private Long productId;
    private Long typeId;
    private Long ownerId;
    private Integer deviceCount;
    private String description;
}