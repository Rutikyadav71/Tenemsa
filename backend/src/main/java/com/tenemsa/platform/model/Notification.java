package com.tenemsa.platform.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "leave_id")
    private Long leaveId;

    public Notification() {}

    public Notification(String message) {
        this.message = message;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    public Notification(String message, Long leaveId) {
        this.message = message;
        this.leaveId = leaveId;
        this.read = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getLeaveId() {
        return leaveId;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setLeaveId(Long leaveId) {
        this.leaveId = leaveId;
    }
}
