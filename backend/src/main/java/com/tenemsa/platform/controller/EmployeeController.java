package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.UserResponseDTO;
import com.tenemsa.platform.mapper.UserMapper;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.security.SecurityRoles;
import com.tenemsa.platform.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Employees", description = "Company-scoped employee management (admin-only except self-service reads)")
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService service;

    @Autowired
    private AuthContext authContext;

    @Autowired
    private UserMapper userMapper;

    @Operation(summary = "List employees in the caller's company",
            description = "Paginated. Defaults to 20 per page, sorted by name. " +
                    "Returns UserResponseDTO, not the raw entity — see UserResponseDTO's class comment.")
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @GetMapping
    public Page<UserResponseDTO> getAll(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.getAll(authContext.currentCompanyId(), pageable).map(userMapper::toDto);
    }

    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN') or @employeeSecurity.isSelf(#empId)")
    @GetMapping("/{empId}")
    public UserResponseDTO getById(@PathVariable int empId) {
        return userMapper.toDto(service.getById(authContext.currentCompanyId(), empId));
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PostMapping
    public UserResponseDTO create(@RequestBody User user) {
        return userMapper.toDto(service.create(authContext.currentCompanyId(), user));
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PutMapping("/{id}")
    public UserResponseDTO update(@PathVariable("id") int empId, @RequestBody User user) {
        return userMapper.toDto(service.update(authContext.currentCompanyId(), empId, user));
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") int empId) {
        service.delete(authContext.currentCompanyId(), empId);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @GetMapping("/search")
    public List<UserResponseDTO> searchEmployees(@RequestParam String keyword) {
        return userMapper.toDtoList(service.searchEmployees(authContext.currentCompanyId(), keyword));
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PostMapping("/delete-multiple")
    public void deleteMultiple(@RequestBody List<Integer> empIds) {
        service.deleteMultiple(authContext.currentCompanyId(), empIds);
    }
}
