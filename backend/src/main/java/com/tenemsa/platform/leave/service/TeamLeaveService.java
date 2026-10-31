package com.tenemsa.platform.leave.service;

import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.leave.dto.TeamLeaveResponse;
import com.tenemsa.platform.leave.model.Leave;
import com.tenemsa.platform.leave.model.LeaveStatus;
import com.tenemsa.platform.leave.repository.LeaveRepository;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.team.service.TeamSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The leave requests a person is responsible for deciding:
 *  - Owner / HR / Admin (leave:view:any): every request in the company.
 *  - Manager: only people on teams they lead, never themselves.
 * Each row says who else in the same group is off on overlapping days, so the decision is informed.
 */
@Service
public class TeamLeaveService {

    private final LeaveRepository leaves;
    private final UserRepository users;
    private final TeamSecurity teamSecurity;
    private final AuthContext authContext;

    public TeamLeaveService(LeaveRepository leaves, UserRepository users, TeamSecurity teamSecurity, AuthContext authContext) {
        this.leaves = leaves;
        this.users = users;
        this.teamSecurity = teamSecurity;
        this.authContext = authContext;
    }

    /** status: PENDING, APPROVED, REJECTED or ALL (null means PENDING). */
    public List<TeamLeaveResponse> list(String statusFilter) {
        Long companyId = authContext.currentCompanyId();
        User me = users.findByEmailAndCompanyId(authContext.currentEmail(), companyId)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
        LeaveStatus wanted = parse(statusFilter);
        boolean everyone = authContext.hasAuthority("leave:view:any");

        List<Leave> scope;
        if (everyone) {
            scope = leaves.findByCompanyId(companyId);
        } else {
            Set<Integer> mine = new HashSet<>(teamSecurity.teamMemberEmpIds());
            mine.remove(me.getEmpId());
            scope = leaves.findByCompanyId(companyId).stream().filter(l -> mine.contains(l.getEmpId())).toList();
        }

        Set<Integer> ids = new HashSet<>();
        for (Leave l : scope) { ids.add(l.getEmpId()); if (l.getDecidedByEmpId() != null) ids.add(l.getDecidedByEmpId()); }
        Map<Integer, User> people = new HashMap<>();
        if (!ids.isEmpty()) {
            for (User u : users.findAllByCompanyIdAndEmpIdIn(companyId, ids)) if (u.getEmpId() != null) people.put(u.getEmpId(), u);
        }

        List<Leave> shown = scope.stream().filter(l -> wanted == null || l.getStatus() == wanted)
                .sorted(Comparator.comparing((Leave l) -> l.getStatus() == LeaveStatus.PENDING ? 0 : 1)
                        .thenComparing(Comparator.comparingInt(Leave::getId).reversed()))
                .toList();

        List<TeamLeaveResponse> out = new ArrayList<>();
        for (Leave l : shown) {
            User person = people.get(l.getEmpId());
            List<String> clashes = scope.stream()
                    .filter(o -> o.getId() != l.getId() && o.getEmpId() != l.getEmpId())
                    .filter(o -> o.getStatus() == LeaveStatus.PENDING || o.getStatus() == LeaveStatus.APPROVED)
                    .filter(o -> !o.getStartDate().isAfter(l.getEndDate()) && !o.getEndDate().isBefore(l.getStartDate()))
                    .map(o -> people.containsKey(o.getEmpId()) ? people.get(o.getEmpId()).getName() : "Employee " + o.getEmpId())
                    .distinct().sorted().toList();
            boolean canDecide = l.getStatus() == LeaveStatus.PENDING
                    && (everyone || (l.getEmpId() != me.getEmpId() && teamSecurity.managesEmployee(l.getEmpId())));
            User decider = l.getDecidedByEmpId() == null ? null : people.get(l.getDecidedByEmpId());
            int days = (int) (l.getEndDate().toEpochDay() - l.getStartDate().toEpochDay()) + 1;
            out.add(new TeamLeaveResponse(l.getId(), l.getEmpId(), person == null ? "Employee " + l.getEmpId() : person.getName(),
                    person == null ? null : person.getDepartment(), l.getStartDate(), l.getEndDate(), days, l.getReason(),
                    l.getStatus(), decider == null ? null : decider.getName(), l.getDecidedAt(), l.getDecisionNote(), clashes, canDecide));
        }
        return out;
    }

    private static LeaveStatus parse(String s) {
        if (s == null || s.isBlank()) return LeaveStatus.PENDING;
        if (s.equalsIgnoreCase("ALL")) return null;
        try { return LeaveStatus.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Unknown status. Use PENDING, APPROVED, REJECTED or ALL."); }
    }
}
