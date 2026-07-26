package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Leave;
import com.tenemsa.platform.model.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRepository extends JpaRepository<Leave, Long> {

    List<Leave> findByEmpId(int empId);

    // Fetch only APPROVED & REJECTED leaves for model training
    List<Leave> findByStatusIn(List<LeaveStatus> statuses);
}
