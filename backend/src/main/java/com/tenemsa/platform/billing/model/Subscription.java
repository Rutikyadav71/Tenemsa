package com.tenemsa.platform.billing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A company's Razorpay subscription. Razorpay is the authority; this row mirrors its state. */
@Entity
@Table(name = "subscriptions", indexes = @Index(name = "idx_subscriptions_company", columnList = "company_id, status"))
public class Subscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "plan_code", nullable = false, length = 30) private String planCode;
    @Column(name = "razorpay_subscription_id", nullable = false, unique = true, length = 60) private String razorpaySubscriptionId;
    /** CREATED, AUTHENTICATED, ACTIVE, PENDING, HALTED, CANCELLED, COMPLETED, EXPIRED. */
    @Column(nullable = false, length = 20) private String status = "CREATED";
    @Column(name = "current_start") private LocalDateTime currentStart;
    @Column(name = "current_end") private LocalDateTime currentEnd;
    @Column(name = "paid_count", nullable = false) private int paidCount;
    @Column(name = "cancel_at_period_end", nullable = false) private boolean cancelAtPeriodEnd;
    /** A plan change waiting for the next renewal. */
    @Column(name = "pending_plan_code", length = 30) private String pendingPlanCode;
    @Column(nullable = false) private boolean trial;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public String getPlanCode() { return planCode; } public void setPlanCode(String v) { planCode = v; }
    public String getRazorpaySubscriptionId() { return razorpaySubscriptionId; } public void setRazorpaySubscriptionId(String v) { razorpaySubscriptionId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; updatedAt = LocalDateTime.now(); }
    public LocalDateTime getCurrentStart() { return currentStart; } public void setCurrentStart(LocalDateTime v) { currentStart = v; }
    public LocalDateTime getCurrentEnd() { return currentEnd; } public void setCurrentEnd(LocalDateTime v) { currentEnd = v; }
    public int getPaidCount() { return paidCount; } public void setPaidCount(int v) { paidCount = v; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; } public void setCancelAtPeriodEnd(boolean v) { cancelAtPeriodEnd = v; }
    public String getPendingPlanCode() { return pendingPlanCode; } public void setPendingPlanCode(String v) { pendingPlanCode = v; }
    public boolean isTrial() { return trial; } public void setTrial(boolean v) { trial = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
