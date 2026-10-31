package com.gesfut.repositories;

import com.gesfut.models.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    Optional<UserEntity> findByResetToken(String resetToken);
    boolean existsByResetToken(String resetToken);

    /** A4 #1: consume el token de reset en un solo UPDATE (atomico). Devuelve 1 si gano, 0 si no existe, vencio o ya se uso. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update UserEntity u set u.password = :pwd, u.passwordChangedAt = :changedAtUtc, u.resetToken = null, u.resetTokenExpiration = null where u.resetToken = :key and u.resetTokenExpiration > :now")
    int consumeResetToken(@org.springframework.data.repository.query.Param("key") String key, @org.springframework.data.repository.query.Param("pwd") String pwd, @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now, @org.springframework.data.repository.query.Param("changedAtUtc") java.time.LocalDateTime changedAtUtc);

    /** A4 #1: consume el token de verificacion en un solo UPDATE (atomico). */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update UserEntity u set u.emailVerified = true, u.resetToken = null, u.resetTokenExpiration = null where u.resetToken = :key and u.resetTokenExpiration > :now")
    int consumeVerifyToken(@org.springframework.data.repository.query.Param("key") String key, @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
}
