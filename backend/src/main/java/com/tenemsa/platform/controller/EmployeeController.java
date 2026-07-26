package com.tenemsa.platform.controller;

import com.tenemsa.platform.model.User;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.security.SecurityRoles;
import com.tenemsa.platform.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService service;

    @Autowired
    private AuthContext authContext;

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @GetMapping
    public List<User> getAll() {
        return service.getAll(authContext.currentCompanyId());
    }

    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN') or @employeeSecurity.isSelf(#empId)")
    @GetMapping("/{empId}")
    public User getById(@PathVariable int empId) {
        return service.getById(authContext.currentCompanyId(), empId);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PostMapping
    public User create(@RequestBody User user) {
        return service.create(authContext.currentCompanyId(), user);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PutMapping("/{id}")
    public User update(@PathVariable("id") int empId, @RequestBody User user) {
        return service.update(authContext.currentCompanyId(), empId, user);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") int empId) {
        service.delete(authContext.currentCompanyId(), empId);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @GetMapping("/search")
    public List<User> searchEmployees(@RequestParam String keyword) {
        return service.searchEmployees(authContext.currentCompanyId(), keyword);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PostMapping("/delete-multiple")
    public void deleteMultiple(@RequestBody List<Integer> empIds) {
        service.deleteMultiple(authContext.currentCompanyId(), empIds);
    }
}
