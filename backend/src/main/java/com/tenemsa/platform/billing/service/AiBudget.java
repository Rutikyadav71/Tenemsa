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
    private final com.tenemsa.platform.billing.repository.TokenTopupRepository topups;

    public AiBudget(AiRequestLogRepository logs, CompanyLimits limits, com.tenemsa.platform.billing.repository.TokenTopupRepository topups) {
        this.logs = logs; this.limits = limits; this.topups = topups;
    }

    public static String period() { return java.time.YearMonth.now().toString(); }

    /** Extra tokens bought this month. */
    public long topupTokens(Long companyId) { return topups.paidTokens(companyId, period()); }

    public static LocalDateTime monthStart() { return LocalDate.now().withDayOfMonth(1).atStartOfDay(); }
    public static LocalDate nextReset() { return LocalDate.now().withDayOfMonth(1).plusMonths(1); }

    public long tokensUsedThisMonth(Long companyId) { return logs.sumTokensSince(companyId, monthStart()); }

    /** The company's budget for the month (plan or override, plus tokens bought this month), or null when it has none (unlimited). */
    public Long budget(Long companyId) {
        Long base = limits.of(companyId).monthlyTokens();
        return base == null ? null : base + topupTokens(companyId);
    }

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
