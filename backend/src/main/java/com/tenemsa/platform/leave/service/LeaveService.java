package com.tenemsa.platform.leave.service;

import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.leave.model.Leave;
import com.tenemsa.platform.leave.model.LeaveStatus;
import com.tenemsa.platform.leave.repository.LeaveRepository;
import com.tenemsa.platform.notification.model.Notification;
import com.tenemsa.platform.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LeaveService {

    @Autowired
    private LeaveRepository leaveRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepo;

    private static final String ML_API_URL = "http://localhost:5000/predict-leave";

    // TENANT ISOLATION (Phase 4): every Leave row carries company_id and every
    // lookup below is scoped by it, so one company can never read, approve,
    // delete or train-on another company's leave requests even if it guesses ids.
    private Notification notify(Long companyId, String message, int leaveId) {
        Notification n = new Notification(message, Long.valueOf(leaveId));
        n.setCompanyId(companyId);
        return notificationRepo.save(n);
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

        notify(companyId, "New leave request submitted by Emp ID: " + savedLeave.getEmpId(), savedLeave.getId());

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

        notify(companyId, "Leave " + status.name().toLowerCase() +
                " for Emp ID: " + updatedLeave.getEmpId(), updatedLeave.getId());

        return updatedLeave;
    }

    public void deleteLeave(Long companyId, Long id) {
        Leave leave = leaveRepository.findByIdAndCompanyId(id.intValue(), companyId).orElse(null);

        if (leave != null) {
            leaveRepository.delete(leave);
            notify(companyId, "Leave deleted for Emp ID: " + leave.getEmpId(), leave.getId());
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
