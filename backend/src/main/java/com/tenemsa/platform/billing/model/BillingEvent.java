package com.tenemsa.platform.billing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One thing Razorpay told us. The unique event id makes repeated deliveries harmless. */
@Entity
@Table(name = "billing_events", indexes = @Index(name = "idx_billing_events_company", columnList = "company_id, created_at"))
public class BillingEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id") private Long companyId;
    @Column(name = "razorpay_event_id", unique = true, length = 80) private String razorpayEventId;
    @Column(name = "event_type", nullable = false, length = 60) private String eventType;
    @Column(name = "razorpay_payment_id", length = 60) private String razorpayPaymentId;
    @Column(name = "amount_paise") private Long amountPaise;
    @Column(length = 20) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public String getRazorpayEventId() { return razorpayEventId; } public void setRazorpayEventId(String v) { razorpayEventId = v; }
    public String getEventType() { return eventType; } public void setEventType(String v) { eventType = v; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; } public void setRazorpayPaymentId(String v) { razorpayPaymentId = v; }
    public Long getAmountPaise() { return amountPaise; } public void setAmountPaise(Long v) { amountPaise = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
