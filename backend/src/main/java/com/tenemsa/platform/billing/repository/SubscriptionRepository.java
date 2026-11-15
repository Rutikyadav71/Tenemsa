package com.tenemsa.platform.billing.repository;

import com.tenemsa.platform.billing.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByRazorpaySubscriptionId(String id);
    List<Subscription> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    List<Subscription> findByStatusIn(Collection<String> statuses);
    boolean existsByCompanyIdAndStatusIn(Long companyId, Collection<String> statuses);
    long countByPlanCodeAndStatus(String planCode, String status);
}
