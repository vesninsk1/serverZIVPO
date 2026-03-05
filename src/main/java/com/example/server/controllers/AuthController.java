package com.example.server.controllers;

import com.example.server.entities.User;
import com.example.server.entities.UserSession;
import com.example.server.models.ApplicationUserRole;
import com.example.server.models.SessionStatus;
import com.example.server.models.AuthenticationRequest;
import com.example.server.models.AuthenticationResponse;
import com.example.server.models.RefreshTokenRequest;
import com.example.server.repositories.UserRepository;
import com.example.server.repositories.UserSessionRepository;
import com.example.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    private static final Pattern PASSWORD_PATTERN = 
        Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$");
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegistrationRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email обязателен для заполнения"));
            }

            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Пароль обязателен для заполнения"));
            }
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email уже существует"));
            }

            if (!isPasswordValid(request.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", 
                            "Длина пароля должна составлять не менее 8 символов, содержать как минимум одну цифру, " +
                            "одну строчную букву, одну заглавную букву, один специальный символ (@#$%^&+=!), " +
                            "и не содержать пробелов"));
            }

            User user = User.builder()
                    .name(request.getName() != null ? request.getName() : request.getEmail().split("@")[0])
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(ApplicationUserRole.USER)
                    .build();

            User savedUser = userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Пользователь успешно зарегистрирован",
                    "email", savedUser.getEmail(),
                    "name", savedUser.getName(),
                    "role", savedUser.getRole().name(),
                    "id", savedUser.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ошибка при регистрации: " + e.getMessage()));
        }
    }
    @PreAuthorize("hasAuthority('modify')")
    @PostMapping("/register-admin")
    public ResponseEntity<?> registerAdmin(@RequestBody RegistrationRequest request) {
        try {
            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email обязателен для заполнения"));
            }

            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Пароль обязателен для заполнения"));
            }

            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Email уже существует"));
            }

            if (!isPasswordValid(request.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", 
                            "Длина пароля должна составлять не менее 8 символов, содержать как минимум одну цифру, " +
                            "одну строчную букву, одну заглавную букву, один специальный символ (@#$%^&+=!), " +
                            "и не содержать пробелов"));
            }

            User user = User.builder()
                    .name(request.getName() != null ? request.getName() : request.getEmail().split("@")[0])
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(ApplicationUserRole.ADMIN)
                    .build();

            User savedUser = userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Администратор успешно зарегистрирован",
                    "email", savedUser.getEmail(),
                    "name", savedUser.getName(),
                    "role", savedUser.getRole().name(),
                    "id", savedUser.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ошибка при регистрации администратора: " + e.getMessage()));
        }
    }

    @GetMapping("/password-requirements")
    public ResponseEntity<Map<String, String>> getPasswordRequirements() {
        return ResponseEntity.ok(Map.of(
            "requirements", 
            "Длина пароля должна составлять не менее 8 символов, содержать как минимум одну цифру, " +
                        "одну строчную букву, одну заглавную букву, один специальный символ (@#$%^&+=!), " +
                        "и не содержать пробелов"
        ));
    }
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthenticationRequest request) {
        try {
            String email = request.getEmail();
            String password = request.getPassword();
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Неверные учетные данные"));
            }

            try {
                authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
                );
            } catch (BadCredentialsException e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Неверные учетные данные"));
            }

            User user = userOpt.get();
            String accessToken = jwtTokenProvider.generateAccessToken(user);
            String tempRefreshToken = jwtTokenProvider.generateRefreshToken(user, "temp");
            UserSession userSession = UserSession.builder()
                    .userId(user.getId())
                    .refreshToken(tempRefreshToken) 
                    .status(SessionStatus.ACTIVE)
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .build();

            UserSession savedSession = userSessionRepository.save(userSession);
            String finalRefreshToken = jwtTokenProvider.generateRefreshToken(user, savedSession.getId().toString());
            userSession.setRefreshToken(finalRefreshToken);
            userSessionRepository.save(userSession);

            return ResponseEntity.ok(new AuthenticationResponse(
                    email,
                    accessToken,
                    finalRefreshToken
            ));

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Неверные учетные данные"));
        }
    }
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            if (!jwtTokenProvider.validateRefreshToken(request.getRefreshToken())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Недействительный refresh token"));
            }

            String sessionId = jwtTokenProvider.getSessionIdFromRefreshToken(request.getRefreshToken());
            Long userId = jwtTokenProvider.getUserIdFromRefreshToken(request.getRefreshToken());

            Optional<UserSession> sessionOpt = userSessionRepository.findById(Long.parseLong(sessionId));
            if (sessionOpt.isEmpty() || !sessionOpt.get().getStatus().equals(SessionStatus.ACTIVE)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Сессия не активна"));
            }

            UserSession oldSession = sessionOpt.get();
            if (!oldSession.getRefreshToken().equals(request.getRefreshToken())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Недействительный refresh token"));
                }
            if (!oldSession.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Доступ запрещен"));
                }            
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Пользователь не найден"));
            }
            User user = userOpt.get();
            oldSession.setStatus(SessionStatus.REFRESHED);
            oldSession.setRevokedAt(LocalDateTime.now());
            userSessionRepository.save(oldSession);

            UserSession newSession = UserSession.builder()
                    .userId(user.getId())
                    .status(SessionStatus.ACTIVE)
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .build();

            UserSession savedSession = userSessionRepository.save(newSession);
            String newAccessToken = jwtTokenProvider.generateAccessToken(user);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(user, savedSession.getId().toString());

            newSession.setRefreshToken(newRefreshToken);
            userSessionRepository.save(newSession);

            return ResponseEntity.ok(new AuthenticationResponse(
                    user.getEmail(),
                    newAccessToken,
                    newRefreshToken
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Ошибка обновления токена"));
        }
    }

    private boolean isPasswordValid(String password) {
        return PASSWORD_PATTERN.matcher(password).matches();
    }

    public static class RegistrationRequest {
        private String name;
        private String email;
        private String password;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}