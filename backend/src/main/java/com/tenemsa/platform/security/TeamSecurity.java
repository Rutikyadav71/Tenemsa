package com.tenemsa.platform.security;

import com.tenemsa.platform.model.Team;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.TeamRepository;
import com.tenemsa.platform.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves "is this specific request within the caller's own-team scope" —
 * the data-level half of permissions like leave:approve:team,
 * attendance:view:team, team:manage:own. Holding the permission string only
 * establishes that a Manager has SOME team-scoped capability; this answers
 * whether a particular target (an employee, a team) is actually one they
 * lead. Same architectural role as EmployeeSecurity.isSelf() — a resolver
 * bean referenced from @PreAuthorize SpEL, not a general-purpose service.
 */
@Component
public class TeamSecurity {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AuthContext authContext;

    public TeamSecurity(TeamRepository teamRepository, UserRepository userRepository, AuthContext authContext) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.authContext = authContext;
    }

    /** Caller identity within their tenant: empId is only meaningful together with companyId. */
    private record CallerContext(Integer empId, Long companyId) {}

    /** Is the caller the lead of the given team (in their own company)? */
    public boolean isTeamLead(Long teamId) {
        CallerContext ctx = caller();
        if (ctx == null) return false;
        return teamRepository.findByIdAndCompanyId(teamId, ctx.companyId())
                .map(t -> ctx.empId().equals(t.getTeamLeadId()))
                .orElse(false);
    }

    /** Is the target employee a member of any team the caller leads? Used for
     *  Manager-scoped leave approval / attendance visibility on a specific employee.
     *  Scoped by company_id (Phase 4), so a lead in company A can never "manage"
     *  an employee id that happens to exist in company B. */
    public boolean managesEmployee(int targetEmpId) {
        CallerContext ctx = caller();
        if (ctx == null) return false;
        List<Team> ledTeams = teamRepository.findByCompanyIdAndTeamLeadId(ctx.companyId(), ctx.empId());
        return ledTeams.stream().anyMatch(t -> t.getMemberIds().contains(targetEmpId));
    }

    /** Every empId across every team the caller leads, deduplicated. Used by
     *  EmployeeController.getAll() to scope the employee list for a Manager
     *  (holds user:view:team, not user:view:any). */
    public Set<Integer> teamMemberEmpIds() {
        CallerContext ctx = caller();
        if (ctx == null) return Set.of();
        return teamRepository.findByCompanyIdAndTeamLeadId(ctx.companyId(), ctx.empId()).stream()
                .flatMap(t -> t.getMemberIds().stream())
                .collect(Collectors.toSet());
    }

    private CallerContext caller() {
        String email = authContext.currentEmail();
        Long companyId = authContext.currentCompanyId();
        if (email == null || companyId == null) return null;
        return userRepository.findByEmailAndCompanyId(email, companyId)
                .map(u -> new CallerContext(u.getEmpId(), companyId))
                .orElse(null);
    }
}
