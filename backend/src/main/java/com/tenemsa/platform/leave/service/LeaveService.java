package com.tenemsa.platform.leave.service;

import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.leave.model.Leave;
import com.tenemsa.platform.leave.model.LeaveStatus;
import com.tenemsa.platform.leave.repository.LeaveRepository;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.notification.service.NotificationService;
import com.tenemsa.platform.security.rbac.Permission;
import com.tenemsa.platform.security.rbac.RolePermissionRegistry;
import com.tenemsa.platform.team.model.Team;
import com.tenemsa.platform.team.repository.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LeaveService {

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TeamRepository teamRepository;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy");

    private static final String ML_API_URL = "http://localhost:5000/predict-leave";

    // TENANT ISOLATION (Phase 4): every Leave row carries company_id and every
    // lookup below is scoped by it, so one company can never read, approve,
    // delete or train-on another company's leave requests even if it guesses ids.
    // Phase 8: notifications are personal (see NotificationService) instead of one company-wide feed.

    private String nameOf(Long companyId, int empId) {
        return userRepository.findAllByCompanyIdAndEmpIdIn(companyId, List.of(empId)).stream()
                .map(User::getName).findFirst().orElse("Employee " + empId);
    }

    private static String describe(Leave l) {
        int days = (int) (l.getEndDate().toEpochDay() - l.getStartDate().toEpochDay()) + 1;
        return l.getStartDate().format(DAY) + (days > 1 ? " to " + l.getEndDate().format(DAY) : "")
                + " (" + days + (days == 1 ? " day)" : " days)");
    }

    /** The leads of the applicant's teams plus everyone whose role may approve any leave, except the applicant. */
    private Set<Integer> approversFor(Long companyId, int applicantEmpId) {
        Set<Integer> out = new LinkedHashSet<>();
        for (Team t : teamRepository.findByCompanyIdAndMemberIdsContaining(companyId, applicantEmpId)) {
            if (t.getTeamLeadId() != null) out.add(t.getTeamLeadId());
        }
        for (com.tenemsa.platform.employee.model.Role role : com.tenemsa.platform.employee.model.Role.values()) {
            if (RolePermissionRegistry.permissionsFor(role).contains(Permission.LEAVE_APPROVE_ANY)) {
                for (User u : userRepository.findAllByCompanyIdAndRole(companyId, role)) {
                    if (u.getEmpId() != null && !u.isFrozen()) out.add(u.getEmpId());
                }
            }
        }
        out.remove(applicantEmpId);
        return out;
    }

    private Leave requireLeave(Long companyId, Long id) {
        return leaveRepository.findByIdAndCompanyId(id.intValue(), companyId)
                .orElseThrow(() -> new RuntimeException("Leave not found with ID: " + id));
    }

    public Leave applyLeave(Leave leave, Long companyId) {

        boolean exists = userRepository.existsByCompanyIdAndEmpId(companyId, leave.getEmpId());
        if (!exists) {
            throw new RuntimeException(
                    "Employee with empId " + leave.getEmpId() + " does not exist."
            );
        }

        leave.setId(0);                 // create only
        leave.setCompanyId(companyId);  // never trust a client-supplied companyId
        leave.setStatus(LeaveStatus.PENDING);
        Leave savedLeave = leaveRepository.save(leave);

        notificationService.send(companyId, approversFor(companyId, savedLeave.getEmpId()), "LEAVE_REQUESTED",
                nameOf(companyId, savedLeave.getEmpId()) + " requested leave: " + describe(savedLeave), Long.valueOf(savedLeave.getId()));

        return savedLeave;
    }

    public List<Leave> getAllLeaves(Long companyId) {
        return leaveRepository.findByCompanyId(companyId);
    }

    public List<Leave> getLeavesByEmpId(Long companyId, int empId) {
        return leaveRepository.findByCompanyIdAndEmpId(companyId, empId);
    }

    public Leave updateLeaveStatus(Long companyId, Long id, LeaveStatus status) {
        Leave leave = requireLeave(companyId, id);

        leave.setStatus(status);
        Leave updatedLeave = leaveRepository.save(leave);

        notificationService.send(companyId, updatedLeave.getEmpId(), "LEAVE_DECIDED",
                "Your leave " + describe(updatedLeave) + " is now " + status.name().toLowerCase() + ".", Long.valueOf(updatedLeave.getId()));

        return updatedLeave;
    }

    /**
     * A manager's or approver's decision on a PENDING request. Differs from updateLeaveStatus (the Owner/HR "set any
     * status" path): it only accepts requests still waiting, a rejection needs a reason, and it records who decided,
     * when, and the note, which the employee receives in their notification.
     */
    public Leave decide(Long companyId, Long id, LeaveStatus decision, String note, Integer deciderEmpId) {
        if (decision != LeaveStatus.APPROVED && decision != LeaveStatus.REJECTED) {
            throw new IllegalArgumentException("A decision is either approve or reject.");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (decision == LeaveStatus.REJECTED && (cleanNote == null || cleanNote.length() < 3)) {
            throw new IllegalArgumentException("Please give a reason when rejecting leave, so the employee knows why.");
        }
        Leave leave = requireLeave(companyId, id);
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalArgumentException("This request was already " + leave.getStatus().name().toLowerCase() + ".");
        }
        leave.setStatus(decision);
        leave.setDecidedByEmpId(deciderEmpId);
        leave.setDecidedAt(LocalDateTime.now());
        leave.setDecisionNote(cleanNote);
        Leave saved = leaveRepository.save(leave);

        String by = deciderEmpId == null ? "your approver" : nameOf(companyId, deciderEmpId);
        notificationService.send(companyId, saved.getEmpId(), "LEAVE_DECIDED",
                "Your leave " + describe(saved) + " was " + decision.name().toLowerCase() + " by " + by
                        + (cleanNote == null ? "." : ": " + cleanNote), Long.valueOf(saved.getId()));
        return saved;
    }

    public void deleteLeave(Long companyId, Long id) {
        Leave leave = leaveRepository.findByIdAndCompanyId(id.intValue(), companyId).orElse(null);

        if (leave != null) {
            leaveRepository.delete(leave);
            notificationService.send(companyId, leave.getEmpId(), "LEAVE_DELETED",
                    "Your leave request for " + describe(leave) + " was removed.", null);
        }
    }

    public Leave getLeaveById(Long companyId, Long id) {
        return requireLeave(companyId, id);
    }

    public Map<String, Object> getLeaveMLInsight(Long companyId, Long id) {

        Leave leave = requireLeave(companyId, id);

        int leaveDays = calculateLeaveDays(
                leave.getStartDate(),
                leave.getEndDate()
        );

        int leaveMonth = leave.getStartDate().getMonthValue();
        int leaveYear = leave.getStartDate().getYear();

        String leaveType = mapReasonToLeaveType(leave.getReason());
        int empId = leave.getEmpId();

        List<Leave> empLeaves = leaveRepository.findByCompanyIdAndEmpId(companyId, empId);

        long monthlyLeaveDays = empLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .filter(l -> l.getStartDate().getYear() == leaveYear)
                .filter(l -> l.getStartDate().getMonthValue() == leaveMonth)
                .filter(l -> l.getStartDate().isBefore(leave.getStartDate()))
                .mapToLong(l -> calculateLeaveDays(l.getStartDate(), l.getEndDate()))
                .sum();

        long yearlyLeaveDays = empLeaves.stream()
                .filter(l -> l.getStatus() == LeaveStatus.APPROVED)
                .filter(l -> l.getStartDate().getYear() == leaveYear)
                .filter(l -> l.getStartDate().isBefore(leave.getStartDate()))
                .mapToLong(l -> calculateLeaveDays(l.getStartDate(), l.getEndDate()))
                .sum();

        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> request = new HashMap<>();
        request.put("leave_type", leaveType);
        request.put("leave_days", leaveDays);
        request.put("leave_month", leaveMonth);
        request.put("past_month_leave_days", monthlyLeaveDays);
        request.put("past_year_leave_days", yearlyLeaveDays);

        Map<String, Object> mlResponse;
        try {
            mlResponse = restTemplate.postForObject(
                    ML_API_URL,
                    request,
                    Map.class
            );
        } catch (Exception e) {
            throw new RuntimeException("ML service unavailable");
        }

        String prediction = mlResponse.get("prediction").toString();
        double confidence = Double.parseDouble(
                mlResponse.get("confidence").toString()
        );

        String riskLevel;
        if (confidence >= 80 || monthlyLeaveDays >= 7) {
            riskLevel = "HIGH";
        } else if (confidence >= 60 || monthlyLeaveDays >= 4) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("prediction", prediction);
        response.put("confidence", confidence);
        response.put("monthlyLeaveDays", monthlyLeaveDays);
        response.put("yearlyLeaveDays", yearlyLeaveDays);
        response.put("riskLevel", riskLevel);

        return response;
    }

    public List<Leave> exportLeavesForTraining(Long companyId) {
        return leaveRepository.findByCompanyIdAndStatusIn(companyId,
                List.of(LeaveStatus.APPROVED, LeaveStatus.REJECTED)
        );
    }

    private int calculateLeaveDays(LocalDate startDate, LocalDate endDate) {
        return (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
    }

    private String mapReasonToLeaveType(String reason) {
        if (reason == null) return "CASUAL";
        reason = reason.toLowerCase();

        if (reason.contains("fever") || reason.contains("sick") || reason.contains("ill"))
            return "SICK";
        if (reason.contains("marriage") || reason.contains("function") || reason.contains("family"))
            return "CASUAL";
        if (reason.contains("vacation") || reason.contains("trip") || reason.contains("holiday"))
            return "PAID";

        return "CASUAL";
    }
}
