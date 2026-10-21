package com.tenemsa.platform.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An emailed invitation to join one company. The raw token only ever exists
 * in the email; the database holds its SHA-256 hash (same scheme as refresh
 * and verification tokens, see TokenService).
 */
@Entity
@Table(
        name = "invitations",
        indexes = {
                @Index(name = "idx_invitations_company", columnList = "company_id"),
                @Index(name = "idx_invitations_token_hash", columnList = "token_hash")
        }
)
public class Invitation {

    public static final String PENDING = "PENDING";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 150)
    private String name;

    // Role name (see Role). Plain text so adding roles never alters the schema.
    @Column(nullable = false, length = 30)
    private String role;

    @Column(length = 100)
    private String department;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // PENDING, ACCEPTED or REVOKED
    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(name = "invited_by_id")
    private Long invitedById;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isUsable() {
        return PENDING.equals(status) && expiresAt.isAfter(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getInvitedById() { return invitedById; }
    public void setInvitedById(Long invitedById) { this.invitedById = invitedById; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
