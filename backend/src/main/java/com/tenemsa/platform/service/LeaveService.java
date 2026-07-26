package com.tenemsa.platform.service;

import com.tenemsa.platform.model.Leave;
import com.tenemsa.platform.model.LeaveStatus;
import com.tenemsa.platform.model.Notification;
import com.tenemsa.platform.repository.UserRepository;
import com.tenemsa.platform.repository.LeaveRepository;
import com.tenemsa.platform.repository.NotificationRepository;
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

    // NOTE ON SCOPE: Leave rows themselves don't carry company_id yet — only
    // this existence check does, because empId stopped being globally unique
    // the moment User became company-scoped, so resolving "does this empId
    // exist" now requires knowing which company to look in. The Leave table
    // (and Attendance, Team) trusting empId uniqueness only holds because
    // Phase 1 still enforces single-company operation end-to-end (see
    // CompanyRepository.findFirstByOrderByIdAsc). Adding company_id to these
    // tables and scoping every query by it is explicitly Phase 4 work
    // (tenant isolation), not silently done here.
    public Leave applyLeave(Leave leave, Long companyId) {

        boolean exists = userRepository.existsByCompanyIdAndEmpId(companyId, leave.getEmpId());
        if (!exists) {
            throw new RuntimeException(
                    "Employee with empId " + leave.getEmpId() + " does not exist."
            );
        }

        leave.setStatus(LeaveStatus.PENDING);
        Leave savedLeave = leaveRepository.save(leave);

        notificationRepo.save(new Notification(
                "New leave request submitted by Emp ID: " + savedLeave.getEmpId(),
                Long.valueOf(savedLeave.getId())

        ));

        return savedLeave;
    }

    public List<Leave> getAllLeaves() {
        return leaveRepository.findAll();
    }

    public List<Leave> getLeavesByEmpId(int empId) {
        return leaveRepository.findByEmpId(empId);
    }

    public Leave updateLeaveStatus(Long id, LeaveStatus status) {
        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Leave not found with ID: " + id)
                );

        leave.setStatus(status);
        Leave updatedLeave = leaveRepository.save(leave);

        notificationRepo.save(new Notification(
                "Leave " + status.name().toLowerCase() +
                        " for Emp ID: " + updatedLeave.getEmpId(),
                Long.valueOf(updatedLeave.getId())
        ));

        return updatedLeave;
    }

    public void deleteLeave(Long id) {
        Leave leave = leaveRepository.findById(id).orElse(null);

        if (leave != null) {
            leaveRepository.deleteById(id);

            notificationRepo.save(new Notification(
                    "Leave deleted for Emp ID: " + leave.getEmpId(),
                    Long.valueOf(leave.getId())
            ));
        }
    }

    public Leave getLeaveById(Long id) {
        return leaveRepository.findById(id.longValue())
                .orElseThrow(() ->
                        new RuntimeException("Leave not found with ID: " + id)
                );
    }

    public Map<String, Object> getLeaveMLInsight(Long id) {

        Leave leave = leaveRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Leave not found with ID: " + id)
                );

        int leaveDays = calculateLeaveDays(
                leave.getStartDate(),
                leave.getEndDate()
        );

        int leaveMonth = leave.getStartDate().getMonthValue();
        int leaveYear = leave.getStartDate().getYear();

        String leaveType = mapReasonToLeaveType(leave.getReason());
        int empId = leave.getEmpId();

        List<Leave> empLeaves = leaveRepository.findByEmpId(empId);

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

    public List<Leave> exportLeavesForTraining() {
        return leaveRepository.findByStatusIn(
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
