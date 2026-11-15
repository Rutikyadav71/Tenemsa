package com.tenemsa.platform.billing.repository;

import com.tenemsa.platform.billing.model.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PlanRepository extends JpaRepository<Plan, String> {
    List<Plan> findAllByOrderBySortOrderAsc();
}
