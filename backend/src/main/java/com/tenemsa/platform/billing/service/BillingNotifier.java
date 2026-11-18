package com.tenemsa.platform.billing.service;

import com.tenemsa.platform.billing.model.BillingEvent;
import com.tenemsa.platform.billing.repository.BillingEventRepository;
import com.tenemsa.platform.employee.model.Role;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Billing emails to a company's owners: receipts, failed payments and AI allowance alerts. Never throws. */
@Service
public class BillingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BillingNotifier.class);

    private final UserRepository users;
    private final EmailService email;
    private final BillingEventRepository events;
    private final AiBudget budget;
    private final JdbcTemplate jdbc;

    public BillingNotifier(UserRepository users, EmailService email, BillingEventRepository events, AiBudget budget, JdbcTemplate jdbc) {
        this.users = users; this.email = email; this.events = events; this.budget = budget; this.jdbc = jdbc;
    }

    public void notifyOwners(Long companyId, String subject, String heading, String message, String[][] rows) {
        try {
            for (User owner : users.findAllByCompanyIdAndRole(companyId, Role.OWNER)) {
                if (owner.getEmail() != null) email.sendBillingEmail(owner.getEmail(), owner.getName(), subject, heading, message, rows);
            }
        } catch (Exception e) {
            log.warn("Could not send a billing email for company {}: {}", companyId, e.toString());
        }
    }

    /**
     * Tells owners once per month when AI use passes 80% and 100% of the allowance. The "once" comes from a billing
     * event with a unique id per company, month and threshold, so repeats and restarts never send a second email.
     * @return how many alert emails were triggered
     */
    public int checkAlerts() {
        int sent = 0;
        String period = AiBudget.period();
        LocalDateTime since = AiBudget.monthStart();
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT company_id, SUM(COALESCE(input_tokens,0) + COALESCE(output_tokens,0)) AS used FROM ai_request_logs "
                    + "WHERE created_at >= ? AND status <> 'RATE_LIMITED' GROUP BY company_id", since);
        } catch (Exception e) {
            log.warn("Could not check AI allowances: {}", e.toString());
            return 0;
        }
        for (Map<String, Object> r : rows) {
            try {
                Long companyId = ((Number) r.get("company_id")).longValue();
                long used = r.get("used") instanceof Number n ? n.longValue() : 0;
                Long b = budget.budget(companyId);
                if (b == null || b <= 0) continue;
                boolean full = used >= b;
                if (used * 100 < b * 80) continue;
                boolean fresh100 = full && record(companyId, 100, period);
                boolean fresh80 = record(companyId, 80, period);
                String[][] facts = {{"Used", String.valueOf(used)}, {"Allowance", String.valueOf(b)}};
                if (fresh100) {
                    notifyOwners(companyId, "Your AI allowance is used up", "AI allowance used up",
                            "Your company has used all of its AI allowance for this month, so AI features are paused until it resets on "
                                    + AiBudget.nextReset() + ". You can buy a token top-up or upgrade the plan in Plan & billing.", facts);
                    sent++;
                } else if (fresh80 && !full) {
                    notifyOwners(companyId, "Your AI allowance is 80% used", "AI allowance 80% used",
                            "Your company has used more than 80% of its AI allowance for this month. It resets on "
                                    + AiBudget.nextReset() + ". You can buy a token top-up or upgrade the plan in Plan & billing before it runs out.", facts);
                    sent++;
                }
            } catch (Exception e) {
                log.warn("AI allowance alert failed: {}", e.toString());
            }
        }
        return sent;
    }

    /** @return true if this is the first time this alert was recorded. */
    private boolean record(Long companyId, int threshold, String period) {
        String id = "alert:" + threshold + ":" + companyId + ":" + period;
        if (events.existsByRazorpayEventId(id)) return false;
        BillingEvent e = new BillingEvent();
        e.setCompanyId(companyId); e.setRazorpayEventId(id); e.setEventType("alert." + threshold); e.setStatus("SENT");
        events.save(e);
        return true;
    }
}
