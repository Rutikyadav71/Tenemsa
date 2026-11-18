package com.tenemsa.platform.billing.repository;

import com.tenemsa.platform.billing.model.BillingDetails;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingDetailsRepository extends JpaRepository<BillingDetails, Long> {}
