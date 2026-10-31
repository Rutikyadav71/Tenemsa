package com.tenemsa.platform.analytics.service;

import com.tenemsa.platform.analytics.dto.AnalyticsOverview;
import com.tenemsa.platform.analytics.dto.AnalyticsOverview.AttendanceStats;
import com.tenemsa.platform.analytics.dto.AnalyticsOverview.Count;
import com.tenemsa.platform.analytics.dto.AnalyticsOverview.LeaveStats;
import com.tenemsa.platform.analytics.dto.AnalyticsOverview.ProjectStats;
import com.tenemsa.platform.analytics.dto.MySummary;
import com.tenemsa.platform.attendance.model.Attendance;
import com.tenemsa.platform.attendance.model.AttendanceStatus;
import com.tenemsa.platform.attendance.repository.AttendanceRepository;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.leave.model.Leave;
import com.tenemsa.platform.leave.model.LeaveStatus;
import com.tenemsa.platform.leave.repository.LeaveRepository;
import com.tenemsa.platform.notification.repository.NotificationRepository;
import com.tenemsa.platform.project.issue.model.Issue;
import com.tenemsa.platform.project.issue.model.IssueStatus;
import com.tenemsa.platform.project.issue.repository.IssueRepository;
import com.tenemsa.platform.security.AuthContext;
import com.tenemsa.platform.team.service.TeamSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Numbers for the role dashboards. Everything is computed from the caller's company; a manager's view is cut down to
 * the people on teams they lead, and an employee only ever gets their own figures. Money (salary, payroll) is
 * deliberately not part of any of this.
 */
@Service
public class AnalyticsService {

    private static final int WINDOW_DAYS = 30;
    private static final int MONTHS = 6;

    private final UserRepository users;
    private final LeaveRepository leaves;
    private final AttendanceRepository attendance;
    private final IssueRepository issues;
    private final NotificationRepository notifications;
    private final TeamSecurity teamSecurity;
    private final AuthContext authContext;

    public AnalyticsService(UserRepository users, LeaveRepository leaves, AttendanceRepository attendance, IssueRepository issues,
                            NotificationRepository notifications, TeamSecurity teamSecurity, AuthContext authContext) {
        this.users = users;
        this.leaves = leaves;
        this.attendance = attendance;
        this.issues = issues;
        this.notifications = notifications;
        this.teamSecurity = teamSecurity;
        this.authContext = authContext;
    }

    private User caller() {
        return users.findByEmailAndCompanyId(authContext.currentEmail(), authContext.currentCompanyId())
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }

    // ---- company / team overview -------------------------------------------------

    public AnalyticsOverview overview() {
        Long companyId = authContext.currentCompanyId();
        boolean company = authContext.hasAuthority("report:view:company");
        if (!company && !authContext.hasAuthority("report:view:team")) throw new AccessDeniedException("You cannot view these reports.");

        Set<Integer> scope = null;                       // null = the whole company
        if (!company) scope = new HashSet<>(teamSecurity.teamMemberEmpIds());

        final Set<Integer> in = scope;
        List<User> people = users.findAllByCompanyId(companyId).stream()
                .filter(u -> !u.isFrozen() && u.getEmpId() != null && (in == null || in.contains(u.getEmpId()))).toList();
        Map<Integer, User> byEmp = new LinkedHashMap<>();
        for (User u : people) byEmp.put(u.getEmpId(), u);

        List<Count> byDepartment = tally(people.stream().map(u -> u.getDepartment() == null || u.getDepartment().isBlank() ? "Unassigned" : u.getDepartment()).toList());
        List<Count> byRole = company ? tally(people.stream().map(u -> u.getRole().name()).toList()) : List.of();

        LocalDate today = LocalDate.now();
        List<Leave> scopedLeaves = leaves.findByCompanyId(companyId).stream().filter(l -> in == null || in.contains(l.getEmpId())).toList();
        LeaveStats leaveStats = leaveStats(scopedLeaves, byEmp, today);

        LocalDate from = today.minusDays(WINDOW_DAYS - 1L);
        List<Attendance> rows = attendance.findByCompanyIdAndDateBetween(companyId, from, today).stream()
                .filter(a -> in == null || in.contains(a.getEmpId())).toList();

        ProjectStats projects = null;
        if (company) {
            Map<IssueStatus, Long> counts = new EnumMap<>(IssueStatus.class);
            for (IssueStatus s : IssueStatus.values()) counts.put(s, 0L);
            for (Object[] r : issues.countByStatus(companyId)) counts.put((IssueStatus) r[0], (Long) r[1]);
            List<Count> byStatus = new ArrayList<>();
            counts.forEach((k, v) -> byStatus.add(new Count(k.name(), v)));
            projects = new ProjectStats(byStatus, issues.countOverdue(companyId, today, IssueStatus.DONE));
        }
        return new AnalyticsOverview(company ? "COMPANY" : "TEAM", people.size(), byDepartment, byRole, leaveStats, attendanceStats(rows), projects);
    }

    private static List<Count> tally(List<String> labels) {
        Map<String, Long> m = new TreeMap<>();
        for (String l : labels) m.merge(l, 1L, Long::sum);
        return m.entrySet().stream().map(e -> new Count(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(Count::value).reversed().thenComparing(Count::label)).toList();
    }

    private static LeaveStats leaveStats(List<Leave> scoped, Map<Integer, User> people, LocalDate today) {
        int pending = (int) scoped.stream().filter(l -> l.getStatus() == LeaveStatus.PENDING).count();
        List<String> off = scoped.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED && !l.getStartDate().isAfter(today) && !l.getEndDate().isBefore(today))
                .map(l -> people.containsKey(l.getEmpId()) ? people.get(l.getEmpId()).getName() : "Employee " + l.getEmpId())
                .distinct().sorted().toList();
        List<Count> months = new ArrayList<>();
        YearMonth now = YearMonth.from(today);
        for (int i = MONTHS - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            months.add(new Count(ym.toString(), approvedDaysIn(scoped, ym)));
        }
        Map<LeaveStatus, Long> status = new EnumMap<>(LeaveStatus.class);
        for (LeaveStatus s : LeaveStatus.values()) status.put(s, 0L);
        for (Leave l : scoped) if (l.getStatus() != null) status.merge(l.getStatus(), 1L, Long::sum);
        List<Count> statusCounts = new ArrayList<>();
        status.forEach((k, v) -> statusCounts.add(new Count(k.name(), v)));
        return new LeaveStats(pending, off, months, statusCounts);
    }

    /** Calendar days of approved leave that fall inside the month (a leave spanning two months counts in both). */
    private static long approvedDaysIn(List<Leave> scoped, YearMonth ym) {
        LocalDate first = ym.atDay(1), last = ym.atEndOfMonth();
        long total = 0;
        for (Leave l : scoped) {
            if (l.getStatus() != LeaveStatus.APPROVED) continue;
            LocalDate s = l.getStartDate().isBefore(first) ? first : l.getStartDate();
            LocalDate e = l.getEndDate().isAfter(last) ? last : l.getEndDate();
            if (!e.isBefore(s)) total += e.toEpochDay() - s.toEpochDay() + 1;
        }
        return total;
    }

    private static AttendanceStats attendanceStats(List<Attendance> rows) {
        long present = 0, late = 0, half = 0, absent = 0, onLeave = 0;
        double hours = 0; int hourRows = 0;
        for (Attendance a : rows) {
            AttendanceStatus st = a.getStatus();
            if (st == AttendanceStatus.PRESENT) present++;
            else if (st == AttendanceStatus.LATE) late++;
            else if (st == AttendanceStatus.HALF_DAY) half++;
            else if (st == AttendanceStatus.ABSENT) absent++;
            else if (st == AttendanceStatus.ON_LEAVE) onLeave++;
            if (a.getWorkHours() != null && a.getWorkHours() > 0) { hours += a.getWorkHours(); hourRows++; }
        }
        return new AttendanceStats(WINDOW_DAYS, present, late, half, absent, onLeave, rate(present + late + half, absent),
                hourRows == 0 ? null : Math.round(hours / hourRows * 10) / 10.0);
    }

    private static Double rate(long attended, long absent) {
        long total = attended + absent;
        return total == 0 ? null : Math.round(attended * 1000.0 / total) / 10.0;
    }

    // ---- the signed-in person's own numbers --------------------------------------

    public MySummary mine() {
        Long companyId = authContext.currentCompanyId();
        User me = caller();
        if (me.getEmpId() == null) return new MySummary(0, 0, null, 0, 0, 0);
        LocalDate today = LocalDate.now();
        int year = today.getYear();

        List<Leave> mine = leaves.findByCompanyIdAndEmpId(companyId, me.getEmpId());
        int approvedDays = 0;
        for (Leave l : mine) {
            if (l.getStatus() != LeaveStatus.APPROVED) continue;
            LocalDate s = l.getStartDate().getYear() < year ? LocalDate.of(year, 1, 1) : l.getStartDate();
            LocalDate e = l.getEndDate().getYear() > year ? LocalDate.of(year, 12, 31) : l.getEndDate();
            if (!e.isBefore(s)) approvedDays += (int) (e.toEpochDay() - s.toEpochDay() + 1);
        }
        int pending = (int) mine.stream().filter(l -> l.getStatus() == LeaveStatus.PENDING).count();

        List<Attendance> rows = attendance.findByCompanyIdAndEmpIdAndDateBetween(companyId, me.getEmpId(), today.minusDays(WINDOW_DAYS - 1L), today);
        long attended = rows.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT || a.getStatus() == AttendanceStatus.LATE
                || a.getStatus() == AttendanceStatus.HALF_DAY).count();
        long absent = rows.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT).count();

        List<Issue> open = issues.findByCompanyIdAndAssigneeEmpIdAndStatusNotOrderByDueDateAscIdAsc(companyId, me.getEmpId(), IssueStatus.DONE);
        int overdue = (int) open.stream().filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(today)).count();

        return new MySummary(approvedDays, pending, rate(attended, absent), open.size(), overdue,
                notifications.countUnread(companyId, me.getEmpId(), false));
    }
}
