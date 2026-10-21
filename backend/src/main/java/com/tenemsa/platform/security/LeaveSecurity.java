package com.tenemsa.platform.security;

import com.tenemsa.platform.repository.LeaveRepository;
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

    public LeaveSecurity(LeaveRepository leaveRepository, TeamSecurity teamSecurity, AuthContext authContext) {
        this.leaveRepository = leaveRepository;
        this.teamSecurity = teamSecurity;
        this.authContext = authContext;
    }

    public boolean managesLeave(Long leaveId) {
        return leaveRepository.findByIdAndCompanyId(leaveId.intValue(), authContext.currentCompanyId())
                .map(leave -> teamSecurity.managesEmployee(leave.getEmpId()))
                .orElse(false);
    }
}
