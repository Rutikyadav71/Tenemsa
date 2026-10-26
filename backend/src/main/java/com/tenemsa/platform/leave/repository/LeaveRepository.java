package com.tenemsa.platform.leave.repository;

import com.tenemsa.platform.leave.model.Leave;
import com.tenemsa.platform.leave.model.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every finder is company-scoped (tenant isolation, Phase 4). */
public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByCompanyIdAndEmpId(Long companyId, int empId);

    List<Leave> findByCompanyId(Long companyId);

    Optional<Leave> findByIdAndCompanyId(int id, Long companyId);

    // Only APPROVED & REJECTED leaves of ONE company for model training
    List<Leave> findByCompanyIdAndStatusIn(Long companyId, List<LeaveStatus> statuses);
}
