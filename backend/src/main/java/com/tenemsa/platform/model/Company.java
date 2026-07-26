package com.tenemsa.platform.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Root tenant entity. Every company-owned row (User, Attendance, Leave, Team,
 * Notification, ChatMessage, and everything added in later phases) carries a
 * company_id pointing back here — this is the foundation multi-tenancy is
 * built on, so it's deliberately kept small and stable.
 */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    // URL-safe unique identifier (e.g. "acme-corp"). Not used for routing yet,
    // but Phase 4's company registration/invite flow needs a stable, unique,
    // human-readable handle per company — adding it now avoids a schema change
    // later when nothing depends on it yet.
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    public Company() {}

    public Company(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
