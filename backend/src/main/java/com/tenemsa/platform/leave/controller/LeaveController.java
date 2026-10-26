package com.tenemsa.platform.leave.controller;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.leave.model.Leave;
import com.tenemsa.platform.leave.model.LeaveStatus;
import com.tenemsa.platform.leave.service.LeaveService;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    @Autowired private LeaveService leaveService;
    @Autowired private AuthContext authContext;
    @Autowired private AuditService auditService;
    @Autowired private UserRepository userRepository;

    // Every role holds leave:apply:self (see RolePermissionRegistry) — this
    // is functionally the same gate as the old isAuthenticated(), just
    // expressed as a real permission now instead of "any logged-in user".
    @PreAuthorize("hasAuthority('leave:apply:self')")
    @PostMapping
    public Leave applyLeave(@RequestBody Leave leave) {
        // Phase 4d: previously any user could file leave for ANY empId in the company.
        // Applying for yourself is always allowed; applying for someone else needs
        // leave:approve:any (Owner, HR), the same people who can act on leave anyway.
        Integer callerEmpId = userRepository
                .findByEmailAndCompanyId(authContext.currentEmail(), authContext.currentCompanyId())
                .map(u -> u.getEmpId()).orElse(null);
        boolean self = callerEmpId != null && callerEmpId == leave.getEmpId();
        if (!self && !authContext.hasAuthority("leave:approve:any")) {
            throw new AccessDeniedException("You can only apply for leave for yourself.");
        }
        return leaveService.applyLeave(leave, authContext.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('leave:view:any')")
    @GetMapping
    public List<Leave> getAllLeaves() {
        return leaveService.getAllLeaves(authContext.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('leave:view:any') or " +
            "(hasAuthority('leave:view:team') and @teamSecurity.managesEmployee(#empId)) or " +
            "@employeeSecurity.isSelf(#empId)")
    @GetMapping("/employee/{empId}")
    public List<Leave> getLeavesByEmpId(@PathVariable("empId") int empId) {
        return leaveService.getLeavesByEmpId(authContext.currentCompanyId(), empId);
    }

    // Direct status set — kept as the Owner/HR "any" path only. Manager
    // approval goes through /approve and /reject specifically (below),
    // which are the ones scoped to their own team.
    @PreAuthorize("hasAuthority('leave:approve:any')")
    @PutMapping("/{id}")
    public Leave updateLeaveStatus(@PathVariable Long id, @RequestBody Leave leave) {
        return leaveService.updateLeaveStatus(authContext.currentCompanyId(), id, leave.getStatus());
    }

    // approve/reject/delete/export/ML-insight previously had no @PreAuthorize
    // at all — any authenticated user could act on any leave request or pull
    // the ML training dataset (closed in an earlier phase). Now permission-
    // based, with Manager's approve/reject additionally scoped to their own
    // team via LeaveSecurity — checked in @PreAuthorize, before the mutation
    // runs, not after (see LeaveSecurity's class comment for why that
    // ordering matters for a write endpoint).
    @PreAuthorize("hasAuthority('leave:approve:any') or " +
            "(hasAuthority('leave:approve:team') and @leaveSecurity.managesLeave(#id))")
    @PutMapping("/{id}/approve")
    public Leave approveLeave(@PathVariable Long id) {
        Leave leave = leaveService.updateLeaveStatus(authContext.currentCompanyId(), id, LeaveStatus.APPROVED);
        auditService.recordCurrent(AuditAction.LEAVE_APPROVED, "LEAVE", String.valueOf(id), "Approved leave of empId " + leave.getEmpId());
        return leave;
    }

    @PreAuthorize("hasAuthority('leave:approve:any') or " +
            "(hasAuthority('leave:approve:team') and @leaveSecurity.managesLeave(#id))")
    @PutMapping("/{id}/reject")
    public Leave rejectLeave(@PathVariable Long id) {
        Leave leave = leaveService.updateLeaveStatus(authContext.currentCompanyId(), id, LeaveStatus.REJECTED);
        auditService.recordCurrent(AuditAction.LEAVE_REJECTED, "LEAVE", String.valueOf(id), "Rejected leave of empId " + leave.getEmpId());
        return leave;
    }

    @PreAuthorize("hasAuthority('leave:approve:any')")
    @DeleteMapping("/{id}")
    public void deleteLeave(@PathVariable Long id) {
        leaveService.deleteLeave(authContext.currentCompanyId(), id);
        auditService.recordCurrent(AuditAction.LEAVE_DELETED, "LEAVE", String.valueOf(id), "Deleted leave request " + id);
    }

    @PreAuthorize("hasAuthority('report:view:company')")
    @GetMapping("/admin/export")
    public List<Leave> exportLeavesForMLTraining() {
        return leaveService.exportLeavesForTraining(authContext.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('report:view:company')")
    @GetMapping("/admin/{id}/prediction")
    public Map<String, Object> getLeaveMLInsight(@PathVariable Long id) {
        return leaveService.getLeaveMLInsight(authContext.currentCompanyId(), id);
    }

    // #result / returnObject is safe here specifically because this is a
    // READ — nothing is mutated, so evaluating the check after the fetch
    // (but before the response leaves the method) denies cleanly with no
    // side effect to undo. Contrast with approve/reject above, which
    // couldn't use this same pattern.
    @PostAuthorize("hasAuthority('leave:view:any') or " +
            "(hasAuthority('leave:view:team') and @teamSecurity.managesEmployee(returnObject.empId)) or " +
            "@employeeSecurity.isSelf(returnObject.empId)")
    @GetMapping("/{id}")
    public Leave getLeaveById(@PathVariable Long id) {
        return leaveService.getLeaveById(authContext.currentCompanyId(), id);
    }
}
