package com.tenemsa.platform.service;

import com.tenemsa.platform.model.User;
import java.util.List;

public interface EmployeeService {

    List<User> getAll(Long companyId);

    User getById(Long companyId, int empId);

    User create(Long companyId, User user);

    User update(Long companyId, int empId, User user);

    void delete(Long companyId, int empId);

    List<User> searchEmployees(Long companyId, String keyword);

    void deleteMultiple(Long companyId, List<Integer> empIds);
}
