package com.tenemsa.platform.ai.assistant.service;

import com.tenemsa.platform.ai.assistant.dto.HrAiOutput;
import com.tenemsa.platform.ai.assistant.dto.HrAnswerResponse;
import com.tenemsa.platform.ai.assistant.dto.HrAskRequest;
import com.tenemsa.platform.ai.model.AiFeature;
import com.tenemsa.platform.ai.service.AiGateway;
import com.tenemsa.platform.ai.service.AiResult;
import com.tenemsa.platform.ai.service.PromptCatalog;
import com.tenemsa.platform.ai.service.PromptInput;
import com.tenemsa.platform.attendance.model.Attendance;
import com.tenemsa.platform.attendance.model.AttendanceStatus;
import com.tenemsa.platform.attendance.repository.AttendanceRepository;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.leave.model.Leave;
import com.tenemsa.platform.leave.model.LeaveStatus;
import com.tenemsa.platform.leave.repository.LeaveRepository;
import com.tenemsa.platform.security.AuthContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Answers an employee's questions about THEIR OWN leave and attendance. Privacy rule, enforced here and
 * not by the prompt: the only records ever loaded are the caller's (company id + the caller's own employee
 * number taken from the login, never from the request). The model never sees anyone else's data and has
 * no tools, so it cannot look anything up or change anything.
 */
@Service
public class HrAssistantService {

    private static final int ATTENDANCE_DAYS = 60;
    private static final int LEAVES_SHOWN = 20;

    private final AiGateway gateway;
    private final PromptCatalog prompts;
    private final LeaveRepository leaves;
    private final AttendanceRepository attendance;
    private final UserRepository users;
    private final AuthContext authContext;

    public HrAssistantService(AiGateway gateway, PromptCatalog prompts, LeaveRepository leaves,
                              AttendanceRepository attendance, UserRepository users, AuthContext authContext) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.leaves = leaves;
        this.attendance = attendance;
        this.users = users;
        this.authContext = authContext;
    }

    public HrAnswerResponse ask(HrAskRequest req) {
        Long companyId = authContext.currentCompanyId();
        User me = users.findByEmailAndCompanyId(authContext.currentEmail(), companyId)
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
        if (me.getEmpId() == null) {
            throw new IllegalArgumentException("Your account has no employee record, so there is no leave or attendance to look up.");
        }
        String facts = String.join("\n", factsFor(companyId, me.getEmpId()));

        String system = prompts.text(PromptCatalog.HR_ASSISTANT, PromptCatalog.V1);
        String user = "Today's date: " + LocalDate.now() + "\n\n"
                + "Question:\n" + PromptInput.fence("QUESTION", req.question()) + "\n\n"
                + "Facts about this employee:\n" + PromptInput.fence("FACTS", facts);
        AiResult<HrAiOutput> result = gateway.generate(new AiGateway.AiCall<>(AiFeature.HR_ASSISTANT,
                PromptCatalog.versionLabel(PromptCatalog.HR_ASSISTANT, PromptCatalog.V1), system, user, HrAiOutput.class));

        List<String> used = result.data().factsUsed() == null ? List.of()
                : result.data().factsUsed().stream().filter(s -> s != null && !s.isBlank()).map(String::strip).limit(6).toList();
        return new HrAnswerResponse(result.data().answer().strip(), used, result.model(), result.promptVersion());
    }

    List<String> factsFor(Long companyId, int empId) {
        LocalDate today = LocalDate.now();
        List<String> lines = new ArrayList<>();

        List<Leave> mine = leaves.findByCompanyIdAndEmpId(companyId, empId).stream()
                .sorted(Comparator.comparing(Leave::getStartDate).reversed()).toList();
        long approvedDaysThisYear = mine.stream().filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .mapToLong(l -> daysInYear(l, today.getYear())).sum();
        lines.add("Leave requests on record: " + mine.size() + " (pending " + count(mine, LeaveStatus.PENDING)
                + ", approved " + count(mine, LeaveStatus.APPROVED) + ", rejected " + count(mine, LeaveStatus.REJECTED) + ")");
        lines.add("Approved leave days in " + today.getYear() + ": " + approvedDaysThisYear);
        lines.add("No leave balance or company leave policy is available.");
        for (Leave l : mine.stream().limit(LEAVES_SHOWN).toList()) {
            lines.add("- Leave " + l.getStartDate() + " to " + l.getEndDate() + " (" + (days(l) ) + " day(s), calendar days) "
                    + l.getStatus() + (l.getReason() == null || l.getReason().isBlank() ? "" : ", reason: " + PromptInput.oneLine(l.getReason(), 120)));
        }

        List<Attendance> rows = attendance.findByCompanyIdAndEmpIdAndDateBetween(companyId, empId,
                today.minusDays(ATTENDANCE_DAYS), today).stream().sorted(Comparator.comparing(Attendance::getDate).reversed()).toList();
        Map<AttendanceStatus, Integer> byStatus = new EnumMap<>(AttendanceStatus.class);
        double hours = 0; int hourDays = 0;
        for (Attendance a : rows) {
            byStatus.merge(a.getStatus(), 1, Integer::sum);
            if (a.getWorkHours() != null && a.getWorkHours() > 0) { hours += a.getWorkHours(); hourDays++; }
        }
        lines.add("Attendance in the last " + ATTENDANCE_DAYS + " days: " + rows.size() + " record(s)"
                + (byStatus.isEmpty() ? "" : " - " + byStatus.toString().replaceAll("[{}]", "")));
        if (hourDays > 0) lines.add("Average work hours on days with hours recorded: " + String.format("%.1f", hours / hourDays));
        for (Attendance a : rows.stream().limit(10).toList()) {
            lines.add("- " + a.getDate() + " " + a.getStatus()
                    + (a.getCheckIn() == null ? "" : ", in " + a.getCheckIn())
                    + (a.getCheckOut() == null ? "" : ", out " + a.getCheckOut()));
        }
        return lines;
    }

    private static long count(List<Leave> list, LeaveStatus s) { return list.stream().filter(l -> l.getStatus() == s).count(); }

    private static long days(Leave l) { return ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1; }

    /** Calendar days of this leave that fall inside the given year. */
    private static long daysInYear(Leave l, int year) {
        LocalDate from = l.getStartDate().isBefore(LocalDate.of(year, 1, 1)) ? LocalDate.of(year, 1, 1) : l.getStartDate();
        LocalDate to = l.getEndDate().isAfter(LocalDate.of(year, 12, 31)) ? LocalDate.of(year, 12, 31) : l.getEndDate();
        return to.isBefore(from) ? 0 : ChronoUnit.DAYS.between(from, to) + 1;
    }
}
