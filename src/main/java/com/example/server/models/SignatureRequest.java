package com.example.server.models;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignatureRequest {
    private String threatName;
    private String firstBytesHex;
    private String remainderHashHex;
    private Long remainderLength;
    private String fileType;
    private Long offsetStart;
    private Long offsetEnd;
}