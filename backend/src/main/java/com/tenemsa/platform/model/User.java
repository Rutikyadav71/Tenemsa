package com.tenemsa.platform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Replaces the old separate Employee and Admin entities. They were the same
 * concept (a person who logs in, belongs to a company, has a role) split
 * across two tables for no structural reason — which is what forced role
 * strings to drift out of sync ("ADMIN" in one table, "ROLE_ADMIN" in the
 * other; JwtFilter had a normalization hack to paper over exactly this).
 * One table, one Role enum, no more drift.
 */
@Entity
@Table(
        name = "users",
        indexes = {
                // Every tenant-scoped query filters on company_id — this is the
                // single most important index in a multi-tenant schema.
                @Index(name = "idx_users_company_id", columnList = "company_id"),
                @Index(name = "idx_users_email", columnList = "email")
        },
        uniqueConstraints = {
                // Email stays globally unique for now (not per-company) because
                // login is still "email + password" with no company selector.
                // Scoping email uniqueness to (company_id, email) is planned for
                // Phase 4 alongside the login flow redesign that would actually
                // let two different companies share an employee's email address
                // without an ambiguous lookup.
                @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
                // empId was previously globally unique, which is wrong for
                // multi-tenancy — two unrelated companies would collide over
                // "employee #1". Scoped to per-company instead.
                @UniqueConstraint(name = "uk_users_company_empid", columnNames = {"company_id", "emp_id"})
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // Business-facing employee number, unique per company (not globally).
    // Nullable because not every role necessarily gets one assigned at
    // creation time (e.g. an Owner created via /register bootstrap).
    @Column(name = "emp_id")
    private Integer empId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    // WRITE_ONLY: this field can be set via incoming JSON (registration,
    // creation) but is never serialized back out in a response. Previously
    // Employee/Admin entities were returned directly from controllers with
    // no protection at all — EmployeeController.getAll()/getById()/search()
    // all leaked the BCrypt hash in the JSON response. AdminController patched
    // this in exactly one place (getAllAdmins()) via a manual
    // `.setPassword(null)` loop; every other endpoint was still exposed. This
    // fixes it at the source so no future endpoint can reintroduce the leak.
    //
    // Nullable as of Phase 2: a user who signs up via Google OAuth2 has no
    // password at all — AuthService rejects password-login attempts for
    // such accounts with a clear message rather than failing a null
    // comparison silently.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    public enum AuthProvider { LOCAL, GOOGLE }

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20, columnDefinition = "varchar(20) default 'LOCAL'")
    private AuthProvider authProvider = AuthProvider.LOCAL;

    // Google-authenticated accounts are auto-verified (Google already
    // confirmed the email address); LOCAL accounts start false and go
    // through the email-verification-token flow.
    //
    // columnDefinition sets a DB-level default — without it, Hibernate's
    // ddl-auto=update would try to ALTER TABLE ADD COLUMN ... NOT NULL on an
    // already-populated users table with no default, which Postgres
    // rejects outright. Same landmine class documented at length in
    // migration-phase1.sql; solved here at the annotation level instead
    // since a straightforward default is all that's needed this time.
    @Column(name = "email_verified", nullable = false, columnDefinition = "boolean default false")
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 100)
    private String department;

    // Money should never be a primitive long — no decimal support, and
    // primitive-vs-boxed mismatches were already inconsistent across the old
    // Employee (long) and AuthResponse (Long) types. BigDecimal is the
    // correct type for currency; precision/scale keeps storage exact.
    @Column(precision = 12, scale = 2)
    private BigDecimal salary;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    // Was Boolean (boxed) on Admin, defaulting via defensive null-checking
    // getters (getFrozen() manually did `frozen != null && frozen`). Primitive
    // boolean removes the null case entirely — there's no third state here.
    @Column(nullable = false)
    private boolean frozen = false;

    // Was "isMain" on Admin. Renamed because "primaryOwner" describes what it
    // means (the founding Owner of a company, who can't be frozen/deleted by
    // other admins) rather than a vague "main" flag, and because it no longer
    // needs the getIsMain()/setIsMain() naming workaround now that it's a
    // plain primitive boolean with a standard isXxx() accessor.
    @Column(name = "primary_owner", nullable = false)
    private boolean primaryOwner = false;

    // Audit fields — groundwork for the "Created By / Updated By / Audit
    // Fields" requirement in the roadmap. createdAt/updatedAt are populated
    // automatically by Hibernate; createdBy/updatedBy are left for the
    // service layer to set once there's a real "who's calling" context wired
    // through every write path (Phase 3).
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    public User() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public Integer getEmpId() { return empId; }
    public void setEmpId(Integer empId) { this.empId = empId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }

    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }

    public boolean isPrimaryOwner() { return primaryOwner; }
    public void setPrimaryOwner(boolean primaryOwner) { this.primaryOwner = primaryOwner; }

    public AuthProvider getAuthProvider() { return authProvider; }
    public void setAuthProvider(AuthProvider authProvider) { this.authProvider = authProvider; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
