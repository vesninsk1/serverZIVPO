package com.example.server.controllers;

import com.example.server.entities.User;
import com.example.server.models.ApplicationUserRole;
import com.example.server.repositories.UserRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserRepository userRepository;
    
    @GetMapping
    @PreAuthorize("hasAuthority('modify')") 
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('modify')") 
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        Optional<User> user = userRepository.findById(id);
        return user.map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('modify')") 
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return ResponseEntity.ok("Пользователь успешно удален");
        }
        return ResponseEntity.notFound().build();
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('modify')") 
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return userRepository.findById(id)
            .map(existingUser -> {
                existingUser.setName(userDetails.getName());
                existingUser.setEmail(userDetails.getEmail());
                User updatedUser = userRepository.save(existingUser);
                return ResponseEntity.ok(updatedUser);
            })
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('modify')")
    public ResponseEntity<User> updateUserRole(@PathVariable Long id, @RequestBody RoleUpdateRequest request) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        User existingUser = userOptional.get();
        try {
            ApplicationUserRole newRole = ApplicationUserRole.valueOf(request.getRole().toUpperCase());
            existingUser.setRole(newRole);
            User updatedUser = userRepository.save(existingUser);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    public static class RoleUpdateRequest {
        private String role;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}