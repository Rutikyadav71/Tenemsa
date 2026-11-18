package com.tenemsa.platform.billing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** The legal name, GSTIN and address a company wants on its receipts. One row per company. */
@Entity
@Table(name = "billing_details")
public class BillingDetails {
    @Id @Column(name = "company_id") private Long companyId;
    @Column(name = "legal_name", length = 200) private String legalName;
    @Column(length = 15) private String gstin;
    @Column(length = 300) private String address;
    @Column(length = 100) private String city;
    @Column(length = 100) private String state;
    @Column(length = 12) private String pincode;
    @Column(name = "billing_email", length = 200) private String billingEmail;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getCompanyId() { return companyId; } public void setCompanyId(Long v) { companyId = v; }
    public String getLegalName() { return legalName; } public void setLegalName(String v) { legalName = v; touch(); }
    public String getGstin() { return gstin; } public void setGstin(String v) { gstin = v; touch(); }
    public String getAddress() { return address; } public void setAddress(String v) { address = v; touch(); }
    public String getCity() { return city; } public void setCity(String v) { city = v; touch(); }
    public String getState() { return state; } public void setState(String v) { state = v; touch(); }
    public String getPincode() { return pincode; } public void setPincode(String v) { pincode = v; touch(); }
    public String getBillingEmail() { return billingEmail; } public void setBillingEmail(String v) { billingEmail = v; touch(); }
    private void touch() { updatedAt = LocalDateTime.now(); }
}
