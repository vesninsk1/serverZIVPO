package com.example.server.controllers;

import com.example.server.entities.Signature;
import com.example.server.entities.SignatureAudit;
import com.example.server.entities.SignatureHistory;
import com.example.server.entities.User;
import com.example.server.models.SignatureRequest;
import com.example.server.services.SignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/signatures")
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureService signatureService;

    @GetMapping
    public ResponseEntity<List<Signature>> getAllSignatures() {
        return ResponseEntity.ok(signatureService.getAllActiveSignatures());
    }

    @GetMapping("/export/incremental")
    public ResponseEntity<List<Signature>> getIncremental(@RequestParam String since) {
        try {
            Instant sinceInstant = Instant.parse(since);
            return ResponseEntity.ok(signatureService.getIncrementalSignatures(sinceInstant));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/export/by-ids")
    public ResponseEntity<List<Signature>> getSignaturesByIds(@RequestBody Map<String, List<UUID>> request) {
        List<UUID> ids = request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(signatureService.getSignaturesByIds(ids));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Signature> getSignatureById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(signatureService.getSignatureById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<?> createSignature(@RequestBody SignatureRequest request,
                                              @AuthenticationPrincipal User user) {
        try {
            Signature signature = signatureService.createSignature(request, user.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(signature);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<?> updateSignature(@PathVariable UUID id,
                                             @RequestBody SignatureRequest request,
                                             @AuthenticationPrincipal User user) {
        try {
            Signature signature = signatureService.updateSignature(id, request, user.getEmail());
            return ResponseEntity.ok(signature);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<?> deleteSignature(@PathVariable UUID id,
                                             @AuthenticationPrincipal User user) {
        try {
            signatureService.deleteSignature(id, user.getEmail());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<List<SignatureHistory>> getSignatureHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(signatureService.getHistory(id));
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<List<SignatureAudit>> getSignatureAudit(@PathVariable UUID id) {
        return ResponseEntity.ok(signatureService.getAudit(id));
    }
}