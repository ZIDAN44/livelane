package com.tpm.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("select r from RefreshToken r join fetch r.user where r.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now, r.rotated = false where r.familyId = :familyId")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :now, r.rotated = false where r.user.id = :userId")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken r where r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
