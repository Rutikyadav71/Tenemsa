package com.tenemsa.platform.billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Once a day, moves companies whose paid time has run out back to the Free plan. */
@Configuration
@EnableScheduling
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);
    private final BillingService billing;
    private final BillingNotifier notifier;

    public BillingScheduler(BillingService billing, BillingNotifier notifier) { this.billing = billing; this.notifier = notifier; }

    /** Every half hour: email owners whose company has used 80% or 100% of this month's AI allowance. */
    @Scheduled(fixedDelay = 1_800_000, initialDelay = 120_000)
    public void aiAlerts() {
        try { notifier.checkAlerts(); }
        catch (Exception e) { log.warn("The AI allowance check failed: {}", e.toString()); }
    }

    @Scheduled(cron = "0 5 3 * * *")
    public void reconcile() {
        try {
            int n = billing.reconcile();
            if (n > 0) log.info("Corrected {} subscription(s) from Razorpay's records.", n);
        } catch (Exception e) {
            log.warn("The Razorpay reconcile failed: {}", e.toString());
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void daily() {
        try {
            int n = billing.downgradeExpired();
            if (n > 0) log.info("Moved {} compan{} back to the Free plan.", n, n == 1 ? "y" : "ies");
        } catch (Exception e) {
            log.warn("The daily billing check failed: {}", e.toString());
        }
    }
}
