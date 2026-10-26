package com.tenemsa.platform.dto;

import com.tenemsa.platform.model.Role;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What EmployeeController/AdminController actually return to the frontend
 * instead of the raw User entity. The specific thing this fixes: User.company
 * is a lazy JPA relation — returning User directly means Jackson has to
 * serialize whatever that field currently is (an uninitialized proxy in the
 * LazyInitializationException case, or an initialized-but-still-a-proxy-
 * subclass-instance in the InvalidDefinitionException case). A DTO can't
 * have this problem structurally: companyId/companyName are plain fields,
 * populated once by UserMapper from values already read out of the entity,
 * never a reference to the entity graph itself.
 */
public class UserResponseDTO {
    private Long id;
    private Integer empId;
    private String name;
    private String email;
    private Role role;
    private String department;
    private BigDecimal salary;
    private String profileImageUrl;
    private boolean frozen;
    private boolean primaryOwner;
    private boolean emailVerified;
    private Long companyId;
    private String companyName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getEmpId() { return empId; }
    public void setEmpId(Integer empId) { this.empId = empId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

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

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
