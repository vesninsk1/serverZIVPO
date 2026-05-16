package com.example.server.services;

import com.example.server.config.MinioProperties;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public void uploadFile(String objectName, MultipartFile file) {
        try {
            // создать bucket если не существует
            boolean found = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build()
            );
            if (!found) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build()
                );
                // приватный bucket — политику не выставляем (по умолчанию приватный)
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }
    }

    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .expiry(minioProperties.getPresignedUrlExpiry(), TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }
}