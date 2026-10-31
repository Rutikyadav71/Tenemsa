package com.tenemsa.platform.leave.service;

import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.leave.repository.LeaveRepository;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.team.service.TeamSecurity;
import org.springframework.stereotype.Component;

/**
 * Resolves "does this specific leave request belong to an employee the
 * caller manages" — needed because approve/reject are mutations. Unlike the
 * existing @PostAuthorize pattern on getLeaveById (safe for a read: the
 * check runs after the fetch, before the response is returned, and nothing
 * changed in the meantime), a write endpoint can't wait until after the
 * service method runs to deny — by then the status update is already
 * committed. This has to be checked in @PreAuthorize, before
 * LeaveController calls into LeaveService at all.
 */
@Component
public class LeaveSecurity {

    private final LeaveRepository leaveRepository;
    private final TeamSecurity teamSecurity;
    private final AuthContext authContext;
    private final UserRepository userRepository;

    public LeaveSecurity(LeaveRepository leaveRepository, TeamSecurity teamSecurity, AuthContext authContext,
                         UserRepository userRepository) {
        this.leaveRepository = leaveRepository;
        this.teamSecurity = teamSecurity;
        this.authContext = authContext;
        this.userRepository = userRepository;
    }

    /** True when the request belongs to someone on a team the caller leads. Your own request never counts: nobody approves their own leave through the team path. */
    public boolean managesLeave(Long leaveId) {
        Long companyId = authContext.currentCompanyId();
        Integer callerEmpId = userRepository.findByEmailAndCompanyId(authContext.currentEmail(), companyId)
                .map(u -> u.getEmpId()).orElse(null);
        return leaveRepository.findByIdAndCompanyId(leaveId.intValue(), companyId)
                .map(leave -> (callerEmpId == null || leave.getEmpId() != callerEmpId) && teamSecurity.managesEmployee(leave.getEmpId()))
                .orElse(false);
    }
}
