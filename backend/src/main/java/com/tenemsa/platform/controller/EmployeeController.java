package com.tenemsa.platform.controller;

import com.tenemsa.platform.dto.UserResponseDTO;
import com.tenemsa.platform.mapper.UserMapper;
import com.tenemsa.platform.model.Role;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.security.TeamSecurity;
import com.tenemsa.platform.security.rbac.RoleAssignmentRegistry;
import com.tenemsa.platform.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Employees", description = "Company-scoped employee management")
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService service;

    @Autowired
    private AuthContext authContext;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeamSecurity teamSecurity;

    @Operation(summary = "List employees in the caller's company",
            description = "Owner/Admin/HR see the whole company (paginated). A Manager (user:view:team only, " +
                    "not user:view:any) sees only their own team's members — enforced here, not just gated at " +
                    "the method boundary, since holding EITHER permission reaches this same endpoint.")
    @PreAuthorize("hasAuthority('user:view:any') or hasAuthority('user:view:team')")
    @GetMapping
    public Page<UserResponseDTO> getAll(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        if (authContext.hasAuthority("user:view:any")) {
            return service.getAll(authContext.currentCompanyId(), pageable).map(userMapper::toDto);
        }
        // Manager path: scope to team members only, rather than the
        // company-wide query. Not DB-paginated (Manager team sizes are
        // small in practice) — wrapped as a single Page so the response
        // shape stays consistent for the frontend either way.
        var teamEmpIds = teamSecurity.teamMemberEmpIds();
        List<UserResponseDTO> scoped = userMapper.toDtoList(
                teamEmpIds.isEmpty() ? List.of()
                        : service.getByEmpIds(authContext.currentCompanyId(), teamEmpIds));
        return new PageImpl<>(scoped, pageable, scoped.size());
    }

    @PreAuthorize("hasAuthority('user:view:any') or hasAuthority('user:view:team') or @employeeSecurity.isSelf(#empId)")
    @GetMapping("/{empId}")
    public UserResponseDTO getById(@PathVariable int empId) {
        User target = service.getById(authContext.currentCompanyId(), empId);
        boolean isSelf = target != null && target.getEmail().equals(authContext.currentEmail());
        if (!authContext.hasAuthority("user:view:any") && !teamSecurity.managesEmployee(empId) && !isSelf) {
            // Holds user:view:team but this specific employee isn't on a
            // team the caller leads, and it isn't their own record either.
            throw new AccessDeniedException("This employee is outside your team.");
        }
        return userMapper.toDto(target);
    }

    @Operation(summary = "Create an employee",
            description = "Which target role the caller may assign depends on their own role — " +
                    "Owner: Admin/HR/Manager/Employee/Intern; Admin: Manager only; HR: Employee/Intern only. " +
                    "See RoleAssignmentRegistry.")
    @PreAuthorize("hasAuthority('user:create')")
    @PostMapping
    public UserResponseDTO create(@RequestBody User user) {
        requireAssignable(user.getRole());
        return userMapper.toDto(service.create(authContext.currentCompanyId(), user));
    }

    @PreAuthorize("hasAuthority('user:update')")
    @PutMapping("/{id}")
    public UserResponseDTO update(@PathVariable("id") int empId, @RequestBody User user) {
        return userMapper.toDto(service.update(authContext.currentCompanyId(), empId, user));
    }

    @PreAuthorize("hasAuthority('user:delete')")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") int empId) {
        service.delete(authContext.currentCompanyId(), empId);
    }

    @PreAuthorize("hasAuthority('user:view:any')")
    @GetMapping("/search")
    public List<UserResponseDTO> searchEmployees(@RequestParam String keyword) {
        return userMapper.toDtoList(service.searchEmployees(authContext.currentCompanyId(), keyword));
    }

    @PreAuthorize("hasAuthority('user:delete')")
    @PostMapping("/delete-multiple")
    public void deleteMultiple(@RequestBody List<Integer> empIds) {
        service.deleteMultiple(authContext.currentCompanyId(), empIds);
    }

    // Holding user:create only establishes the caller can create SOME user —
    // RoleAssignmentRegistry answers whether they may create THIS specific
    // target role. E.g. HR holds user:create but must not be able to create
    // another HR account or an Admin by just changing the "role" field in
    // the request body — this is exactly the gap that existed before this
    // phase (any admin-tier caller could set role to anything in the JSON).
    private void requireAssignable(Role targetRole) {
        Role callerRole = authContext.currentRole();
        if (targetRole == null || !RoleAssignmentRegistry.canAssign(callerRole, targetRole)) {
            throw new AccessDeniedException(
                    "Your role (" + callerRole + ") cannot create a user with role " + targetRole + ".");
        }
    }
}
