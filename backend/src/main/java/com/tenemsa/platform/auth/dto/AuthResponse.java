package com.tenemsa.platform.auth.dto;

import java.math.BigDecimal;

/**
 * Previously had four overloaded constructors covering different subsets of
 * fields, one of which (t, r, n, e, adminId, boolean a) silently ignored its
 * last parameter entirely — the "isMain" value was never actually stored
 * through the constructor; AdminController had to call resp.setIsMain(...)
 * separately afterward to make it work. Replaced with a single full
 * constructor + a no-arg constructor for setter-based building, so there's
 * one obvious way to construct this instead of four easy-to-misuse ones.
 */
public class AuthResponse {
    private String token;
    // New in Phase 2 — access tokens are now short-lived (15 min default),
    // so the frontend needs this to silently renew them via /api/auth/refresh
    // instead of forcing a re-login every 15 minutes.
    private String refreshToken;
    private String role;
    private String name;
    private String email;
    private Long id;
    private Integer empId;
    private Long companyId;
    // Added so the frontend can show the user's actual company name instead
    // of hardcoded product branding in the sidebar/navbar — was previously
    // only available as an opaque companyId with no way to resolve it to a
    // display name without a separate lookup.
    private String companyName;
    private String companySlug;
    private String department;
    private BigDecimal salary;
    private Boolean primaryOwner;
    private Boolean emailVerified;

    public AuthResponse() {}

    public AuthResponse(String token, String role, String name, String email) {
        this.token = token;
        this.role = role;
        this.name = name;
        this.email = email;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    // The user's own database primary key. Admin-side login needs this —
    // ChatPage.jsx builds its "ROLE_ID" composite key from it for
    // WebSocket subscriptions. This was silently dropped when AuthResponse
    // was rewritten for Phase 1 (the old constructor had an "adminId"
    // parameter buried in it) — caught and restored before it shipped.
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getEmpId() { return empId; }
    public void setEmpId(Integer empId) { this.empId = empId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getCompanySlug() { return companySlug; }
    public void setCompanySlug(String companySlug) { this.companySlug = companySlug; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }

    // Wire field is "primaryOwner" now, not "isMain" — frontend is being
    // updated in the same pass as this backend change, so there's no reason
    // to keep the legacy wire-compat shim around.
    public Boolean getPrimaryOwner() { return primaryOwner; }
    public void setPrimaryOwner(Boolean primaryOwner) { this.primaryOwner = primaryOwner; }

    public Boolean getEmailVerified() { return emailVerified; }
    public void setEmailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; }
}
