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

    public BillingScheduler(BillingService billing) { this.billing = billing; }

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
