package com.tenemsa.platform.controller;

import com.tenemsa.platform.model.Leave;
import com.tenemsa.platform.model.LeaveStatus;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.security.SecurityRoles;
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

    // Any authenticated user (any role) can apply for their own leave.
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Leave applyLeave(@RequestBody Leave leave) {
        return leaveService.applyLeave(leave, authContext.currentCompanyId());
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @GetMapping
    public List<Leave> getAllLeaves() {
        return leaveService.getAllLeaves();
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN + " or @employeeSecurity.isSelf(#empId)")
    @GetMapping("/employee/{empId}")
    public List<Leave> getLeavesByEmpId(@PathVariable("empId") int empId) {
        return leaveService.getLeavesByEmpId(empId);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PutMapping("/{id}")
    public Leave updateLeaveStatus(@PathVariable Long id, @RequestBody Leave leave) {
        return leaveService.updateLeaveStatus(id, leave.getStatus());
    }

    // These five endpoints (approve, reject, delete, export, ML insight) had
    // no @PreAuthorize at all before this change — any authenticated user,
    // not just admins, could approve/reject/delete any leave request or pull
    // the full leave dataset used to train the ML model. Found while updating
    // this controller's role checks for the new hierarchy; closing it here
    // rather than leaving it for a later phase since it's the same class of
    // gap already fixed on EmployeeController in Phase 0.
    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PutMapping("/{id}/approve")
    public Leave approveLeave(@PathVariable Long id) {
        return leaveService.updateLeaveStatus(id, LeaveStatus.APPROVED);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @PutMapping("/{id}/reject")
    public Leave rejectLeave(@PathVariable Long id) {
        return leaveService.updateLeaveStatus(id, LeaveStatus.REJECTED);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @DeleteMapping("/{id}")
    public void deleteLeave(@PathVariable Long id) {
        leaveService.deleteLeave(id);
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @GetMapping("/admin/export")
    public List<Leave> exportLeavesForMLTraining() {
        return leaveService.exportLeavesForTraining();
    }

    @PreAuthorize(SecurityRoles.IS_ADMIN)
    @GetMapping("/admin/{id}/prediction")
    public Map<String, Object> getLeaveMLInsight(@PathVariable Long id) {
        return leaveService.getLeaveMLInsight(id);
    }

    // #result is only available in @PostAuthorize (evaluated after the method
    // runs, once there's a return value to inspect) — using it in
    // @PreAuthorize would have been a silent bug: either a SpEL evaluation
    // failure at runtime or, worse, an expression that fails open. Admins can
    // see any leave; a non-admin can only see their own, checked after fetch.
    @PostAuthorize(SecurityRoles.IS_ADMIN + " or @employeeSecurity.isSelf(returnObject.empId)")
    @GetMapping("/{id}")
    public Leave getLeaveById(@PathVariable Long id) {
        return leaveService.getLeaveById(id);
    }
}
