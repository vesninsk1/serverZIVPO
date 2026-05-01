package com.example.server.binary;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Фабрика multipart/mixed ответов.
 * Порядок частей фиксирован: сначала manifest.bin, затем data.bin (§3 методички).
 */
@Component
public class MultipartMixedResponseFactory {

    public ResponseEntity<MultiValueMap<String, Object>> create(
            byte[] manifestBytes, byte[] dataBytes) {

        // LinkedMultiValueMap сохраняет порядок вставки (§3 методички)
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("manifest", createPart("manifest.bin", manifestBytes));
        body.add("data",     createPart("data.bin",     dataBytes));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("multipart/mixed"));

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    private HttpEntity<ByteArrayResource> createPart(String filename, byte[] content) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        partHeaders.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());

        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        return new HttpEntity<>(resource, partHeaders);
    }
}