package com.tenemsa.platform.service;

import com.tenemsa.platform.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EmployeeService {

    Page<User> getAll(Long companyId, Pageable pageable);

    // Manager-scoped listing — see EmployeeController.getAll() and
    // TeamSecurity.teamMemberEmpIds().
    List<User> getByEmpIds(Long companyId, java.util.Collection<Integer> empIds);

    User getById(Long companyId, int empId);

    User create(Long companyId, User user);

    User update(Long companyId, int empId, User user);

    void delete(Long companyId, int empId);

    List<User> searchEmployees(Long companyId, String keyword);

    void deleteMultiple(Long companyId, List<Integer> empIds);
}
