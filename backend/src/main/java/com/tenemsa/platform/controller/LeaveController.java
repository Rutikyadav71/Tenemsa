package com.tenemsa.platform.controller;

import com.tenemsa.platform.model.Leave;
import com.tenemsa.platform.model.LeaveStatus;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.service.LeaveService;
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

    // Every role holds leave:apply:self (see RolePermissionRegistry) — this
    // is functionally the same gate as the old isAuthenticated(), just
    // expressed as a real permission now instead of "any logged-in user".
    @PreAuthorize("hasAuthority('leave:apply:self')")
    @PostMapping
    public Leave applyLeave(@RequestBody Leave leave) {
        return leaveService.applyLeave(leave, authContext.currentCompanyId());
    }

    @PreAuthorize("hasAuthority('leave:view:any')")
    @GetMapping
    public List<Leave> getAllLeaves() {
        return leaveService.getAllLeaves();
    }

    @PreAuthorize("hasAuthority('leave:view:any') or " +
            "(hasAuthority('leave:view:team') and @teamSecurity.managesEmployee(#empId)) or " +
            "@employeeSecurity.isSelf(#empId)")
    @GetMapping("/employee/{empId}")
    public List<Leave> getLeavesByEmpId(@PathVariable("empId") int empId) {
        return leaveService.getLeavesByEmpId(empId);
    }

    // Direct status set — kept as the Owner/HR "any" path only. Manager
    // approval goes through /approve and /reject specifically (below),
    // which are the ones scoped to their own team.
    @PreAuthorize("hasAuthority('leave:approve:any')")
    @PutMapping("/{id}")
    public Leave updateLeaveStatus(@PathVariable Long id, @RequestBody Leave leave) {
        return leaveService.updateLeaveStatus(id, leave.getStatus());
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
        return leaveService.updateLeaveStatus(id, LeaveStatus.APPROVED);
    }

    @PreAuthorize("hasAuthority('leave:approve:any') or " +
            "(hasAuthority('leave:approve:team') and @leaveSecurity.managesLeave(#id))")
    @PutMapping("/{id}/reject")
    public Leave rejectLeave(@PathVariable Long id) {
        return leaveService.updateLeaveStatus(id, LeaveStatus.REJECTED);
    }

    @PreAuthorize("hasAuthority('leave:approve:any')")
    @DeleteMapping("/{id}")
    public void deleteLeave(@PathVariable Long id) {
        leaveService.deleteLeave(id);
    }

    @PreAuthorize("hasAuthority('report:view:company')")
    @GetMapping("/admin/export")
    public List<Leave> exportLeavesForMLTraining() {
        return leaveService.exportLeavesForTraining();
    }

    @PreAuthorize("hasAuthority('report:view:company')")
    @GetMapping("/admin/{id}/prediction")
    public Map<String, Object> getLeaveMLInsight(@PathVariable Long id) {
        return leaveService.getLeaveMLInsight(id);
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
        return leaveService.getLeaveById(id);
    }
}
