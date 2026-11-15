package com.tenemsa.platform.billing.repository;

import com.tenemsa.platform.billing.model.BillingEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {
    boolean existsByRazorpayEventId(String id);
    List<BillingEvent> findByCompanyIdAndEventTypeOrderByCreatedAtDesc(Long companyId, String eventType, Pageable pageable);
}
