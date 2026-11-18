package com.tenemsa.platform.billing.repository;

import com.tenemsa.platform.billing.model.TokenTopup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TokenTopupRepository extends JpaRepository<TokenTopup, Long> {
    Optional<TokenTopup> findByRazorpayOrderId(String orderId);

    @Query("select coalesce(sum(t.tokens), 0) from TokenTopup t where t.companyId = :companyId and t.period = :period and t.status = 'PAID'")
    long paidTokens(@Param("companyId") Long companyId, @Param("period") String period);

    List<TokenTopup> findTop10ByCompanyIdAndStatusOrderByCreatedAtDesc(Long companyId, String status);
}
