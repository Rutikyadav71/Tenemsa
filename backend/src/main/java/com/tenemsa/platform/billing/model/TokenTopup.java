package com.tenemsa.platform.billing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One purchase of extra tokens. The tokens count for the calendar month ({@code period}) they were bought in. */
@Entity
@Table(name = "token_topups", indexes = @Index(name = "idx_token_topups_company", columnList = "company_id, period, status"))
public class TokenTopup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "pack_code", nullable = false, length = 30) private String packCode;
    @Column(nullable = false) private long tokens;
    @Column(name = "amount_paise", nullable = false) private int amountPaise;
    /** yyyy-MM */
    @Column(nullable = false, length = 7) private String period;
    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 60) private String razorpayOrderId;
    @Column(name = "razorpay_payment_id", length = 60) private String razorpayPaymentId;
    /** CREATED (waiting for payment) or PAID. */
    @Column(nullable = false, length = 20) private String status = "CREATED";
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public String getPackCode() { return packCode; } public void setPackCode(String v) { packCode = v; }
    public long getTokens() { return tokens; } public void setTokens(long v) { tokens = v; }
    public int getAmountPaise() { return amountPaise; } public void setAmountPaise(int v) { amountPaise = v; }
    public String getPeriod() { return period; } public void setPeriod(String v) { period = v; }
    public String getRazorpayOrderId() { return razorpayOrderId; } public void setRazorpayOrderId(String v) { razorpayOrderId = v; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; } public void setRazorpayPaymentId(String v) { razorpayPaymentId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
