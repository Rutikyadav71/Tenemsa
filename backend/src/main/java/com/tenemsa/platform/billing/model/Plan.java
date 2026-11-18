package com.tenemsa.platform.billing.model;

import jakarta.persistence.*;

/** A plan companies can be on. A null limit means unlimited. Prices are in paise (1/100 rupee). */
@Entity
@Table(name = "plans")
public class Plan {
    @Id @Column(length = 30) private String code;
    @Column(nullable = false, length = 60) private String name;
    @Column(length = 250) private String description;
    @Column(name = "price_paise", nullable = false) private int pricePaise;
    @Column(name = "max_users") private Integer maxUsers;
    @Column(name = "monthly_ai_tokens") private Long monthlyAiTokens;
    @Column(name = "max_connections") private Integer maxConnections;
    @Column(name = "contact_only", nullable = false) private boolean contactOnly;
    @Column(name = "razorpay_plan_id", length = 60) private String razorpayPlanId;
    @Column(name = "trial_days", nullable = false) private int trialDays;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;

    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public int getPricePaise() { return pricePaise; } public void setPricePaise(int v) { pricePaise = v; }
    public Integer getMaxUsers() { return maxUsers; } public void setMaxUsers(Integer v) { maxUsers = v; }
    public Long getMonthlyAiTokens() { return monthlyAiTokens; } public void setMonthlyAiTokens(Long v) { monthlyAiTokens = v; }
    public Integer getMaxConnections() { return maxConnections; } public void setMaxConnections(Integer v) { maxConnections = v; }
    public boolean isContactOnly() { return contactOnly; } public void setContactOnly(boolean v) { contactOnly = v; }
    public String getRazorpayPlanId() { return razorpayPlanId; } public void setRazorpayPlanId(String v) { razorpayPlanId = v; }
    public int getTrialDays() { return trialDays; } public void setTrialDays(int v) { trialDays = v; }
    public int getSortOrder() { return sortOrder; } public void setSortOrder(int v) { sortOrder = v; }
    public boolean isActive() { return active; } public void setActive(boolean v) { active = v; }
}
