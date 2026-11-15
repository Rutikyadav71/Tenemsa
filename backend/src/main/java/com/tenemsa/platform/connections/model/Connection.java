package com.tenemsa.platform.connections.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One person's sign-in to one outside app. The tokens are encrypted; nothing here is ever sent to a browser. */
@Entity
@Table(name = "connections", uniqueConstraints = @UniqueConstraint(name = "uq_connections", columnNames = {"company_id", "user_id", "provider"}))
public class Connection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 30) private String provider;
    @Column(name = "account_label", length = 200) private String accountLabel;
    @Column(name = "access_token_enc", nullable = false, columnDefinition = "TEXT") private String accessTokenEnc;
    @Column(name = "refresh_token_enc", columnDefinition = "TEXT") private String refreshTokenEnc;
    @Column(name = "expires_at") private LocalDateTime expiresAt;
    @Column(length = 500) private String scopes;
    /** ACTIVE, or NEEDS_RECONNECT when the app no longer accepts the saved sign-in. */
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "last_used_at") private LocalDateTime lastUsedAt;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
    public String getProvider() { return provider; } public void setProvider(String v) { provider = v; }
    public String getAccountLabel() { return accountLabel; } public void setAccountLabel(String v) { accountLabel = v; }
    public String getAccessTokenEnc() { return accessTokenEnc; } public void setAccessTokenEnc(String v) { accessTokenEnc = v; }
    public String getRefreshTokenEnc() { return refreshTokenEnc; } public void setRefreshTokenEnc(String v) { refreshTokenEnc = v; }
    public LocalDateTime getExpiresAt() { return expiresAt; } public void setExpiresAt(LocalDateTime v) { expiresAt = v; }
    public String getScopes() { return scopes; } public void setScopes(String v) { scopes = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; } public void setLastUsedAt(LocalDateTime v) { lastUsedAt = v; }
}
