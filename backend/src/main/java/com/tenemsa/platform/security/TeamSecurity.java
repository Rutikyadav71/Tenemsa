package com.tenemsa.platform.security;

import com.tenemsa.platform.model.Team;
import com.tenemsa.platform.model.User;
import com.tenemsa.platform.repository.TeamRepository;
import com.tenemsa.platform.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
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

    public TeamSecurity(TeamRepository teamRepository, UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    /** Is the caller the lead of the given team? */
    public boolean isTeamLead(Long teamId) {
        Integer callerEmpId = callerEmpId();
        if (callerEmpId == null) return false;
        return teamRepository.findById(teamId)
                .map(t -> callerEmpId.equals(t.getTeamLeadId()))
                .orElse(false);
    }

    /** Is the target employee a member of any team the caller leads? Used for
     *  Manager-scoped leave approval / attendance visibility on a specific employee.
     *
     *  NOTE: Team has no company_id yet (documented gap since Phase 1 — see
     *  docs/architecture.md — safe today only because the app is still
     *  functionally single-tenant). findAll() here inherits that same
     *  limitation rather than introducing a new one; TeamController's own
     *  list endpoint has the identical unscoped findAll() already. Phase 4's
     *  tenant-isolation work fixes this at the repository level for every
     *  caller at once, this method included. */
    public boolean managesEmployee(int targetEmpId) {
        Integer callerEmpId = callerEmpId();
        if (callerEmpId == null) return false;
        List<Team> ledTeams = teamRepository.findAll().stream()
                .filter(t -> callerEmpId.equals(t.getTeamLeadId()))
                .toList();
        return ledTeams.stream().anyMatch(t -> t.getMemberIds().contains(targetEmpId));
    }

    /** Every empId across every team the caller leads, deduplicated. Used by
     *  EmployeeController.getAll() to actually scope the employee list for a
     *  Manager (holds user:view:team, not user:view:any) — without this, a
     *  Manager granted "view my team" would otherwise see the whole
     *  company's roster, since the underlying query has no other way to
     *  know which subset "their team" means. */
    public Set<Integer> teamMemberEmpIds() {
        Integer callerEmpId = callerEmpId();
        if (callerEmpId == null) return Set.of();
        return teamRepository.findAll().stream()
                .filter(t -> callerEmpId.equals(t.getTeamLeadId()))
                .flatMap(t -> t.getMemberIds().stream())
                .collect(Collectors.toSet());
    }

    private Integer callerEmpId() {
        String email = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        if (email == null) return null;
        return userRepository.findByEmail(email).map(User::getEmpId).orElse(null);
    }
}
