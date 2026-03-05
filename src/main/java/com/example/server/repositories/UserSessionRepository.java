package com.example.server.repositories;

import com.example.server.entities.UserSession;
import com.example.server.models.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByRefreshTokenAndStatus(String refreshToken, SessionStatus status);
    List<UserSession> findByUserIdAndStatus(Long userId, SessionStatus status);
    
    @Modifying
    @Query("UPDATE UserSession us SET us.status = :status, us.revokedAt = :revokedAt WHERE us.id = :id")
    void updateSessionStatus(@Param("id") Long id, @Param("status") SessionStatus status, @Param("revokedAt") LocalDateTime revokedAt);
    
    @Modifying
    @Query("UPDATE UserSession us SET us.status = 'EXPIRED' WHERE us.expiresAt < :now AND us.status = 'ACTIVE'")
    void expireOldSessions(@Param("now") LocalDateTime now);
    
    boolean existsByRefreshToken(String refreshToken);
    
    Optional<UserSession> findById(Long id);
}