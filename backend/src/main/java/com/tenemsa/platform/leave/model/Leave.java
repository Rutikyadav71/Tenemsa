package com.tenemsa.platform.leave.model;

import jakarta.persistence.*;

import java.time.LocalDate;

// Plain hand-written entity (no Lombok): Hibernate and Jackson both need a real
// no-argument constructor, and Lombok's @NoArgsConstructor was not being generated
// on newer JDKs, which made GET /api/leaves fail with "No default constructor".
@Entity
@Table(name = "leaves", indexes = @Index(name = "idx_leaves_company", columnList = "company_id"))
public class Leave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "emp_id", nullable = false)
    private int empId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private String reason;

    @Enumerated(EnumType.STRING)
    private LeaveStatus status;

    public Leave() {
    }

    public Leave(int id, Long companyId, int empId, LocalDate startDate, LocalDate endDate, String reason, LeaveStatus status) {
        this.id = id;
        this.companyId = companyId;
        this.empId = empId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.status = status;
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Long getCompanyId() { return companyId; }

    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public int getEmpId() {
        return empId;
    }

    public void setEmpId(int empId) {
        this.empId = empId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
    }

}
