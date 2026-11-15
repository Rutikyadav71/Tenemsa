package com.tenemsa.platform.billing.service;

import com.tenemsa.platform.ai.repository.AiRequestLogRepository;
import com.tenemsa.platform.company.service.CompanyLimits;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** The monthly AI token budget. A month is the calendar month in the server's time zone. */
@Service
public class AiBudget {

    private final AiRequestLogRepository logs;
    private final CompanyLimits limits;

    public AiBudget(AiRequestLogRepository logs, CompanyLimits limits) { this.logs = logs; this.limits = limits; }

    public static LocalDateTime monthStart() { return LocalDate.now().withDayOfMonth(1).atStartOfDay(); }
    public static LocalDate nextReset() { return LocalDate.now().withDayOfMonth(1).plusMonths(1); }

    public long tokensUsedThisMonth(Long companyId) { return logs.sumTokensSince(companyId, monthStart()); }

    /** The company's budget for the month, or null when it has none (unlimited). */
    public Long budget(Long companyId) { return limits.of(companyId).monthlyTokens(); }

    /** A message to show when the budget is spent, or null when AI may be used. Fails open on any lookup problem. */
    public String exceededMessage(Long companyId) {
        try {
            Long budget = budget(companyId);
            if (budget == null) return null;
            if (tokensUsedThisMonth(companyId) < budget) return null;
            return "Your company has used its monthly AI allowance. Upgrade the plan in Billing, or wait until " + nextReset() + " when it resets.";
        } catch (Exception e) {
            return null;
        }
    }
}
