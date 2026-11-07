package com.tenemsa.platform.platformadmin.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One of the (at most two) system administrators. Not tied to any company. */
@Entity
@Table(name = "platform_admins")
public class PlatformAdmin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private Integer slot;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, unique = true, length = 255) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "totp_secret_enc", nullable = false) private String totpSecretEnc;
    @Column(name = "totp_enabled", nullable = false) private boolean totpEnabled;
    @Column(name = "last_totp_step") private Long lastTotpStep;
    @Column(name = "failed_attempts", nullable = false) private int failedAttempts;
    @Column(name = "locked_until") private LocalDateTime lockedUntil;
    @Column(name = "last_login_at") private LocalDateTime lastLoginAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Integer getSlot() { return slot; } public void setSlot(Integer v) { slot = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getEmail() { return email; } public void setEmail(String v) { email = v; }
    public String getPasswordHash() { return passwordHash; } public void setPasswordHash(String v) { passwordHash = v; }
    public String getTotpSecretEnc() { return totpSecretEnc; } public void setTotpSecretEnc(String v) { totpSecretEnc = v; }
    public boolean isTotpEnabled() { return totpEnabled; } public void setTotpEnabled(boolean v) { totpEnabled = v; }
    public Long getLastTotpStep() { return lastTotpStep; } public void setLastTotpStep(Long v) { lastTotpStep = v; }
    public int getFailedAttempts() { return failedAttempts; } public void setFailedAttempts(int v) { failedAttempts = v; }
    public LocalDateTime getLockedUntil() { return lockedUntil; } public void setLockedUntil(LocalDateTime v) { lockedUntil = v; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; } public void setLastLoginAt(LocalDateTime v) { lastLoginAt = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
