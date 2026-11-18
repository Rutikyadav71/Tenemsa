package com.tenemsa.platform.billing.model;

import jakarta.persistence.*;

/** A one-off bundle of extra AI tokens a company can buy when its monthly allowance runs out. */
@Entity
@Table(name = "topup_packs")
public class TopupPack {
    @Id @Column(length = 30) private String code;
    @Column(nullable = false, length = 60) private String name;
    @Column(nullable = false) private long tokens;
    @Column(name = "price_paise", nullable = false) private int pricePaise;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;

    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public long getTokens() { return tokens; } public void setTokens(long v) { tokens = v; }
    public int getPricePaise() { return pricePaise; } public void setPricePaise(int v) { pricePaise = v; }
    public int getSortOrder() { return sortOrder; } public void setSortOrder(int v) { sortOrder = v; }
    public boolean isActive() { return active; } public void setActive(boolean v) { active = v; }
}
