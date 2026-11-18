package com.tenemsa.platform.billing.repository;

import com.tenemsa.platform.billing.model.TopupPack;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TopupPackRepository extends JpaRepository<TopupPack, String> {
    List<TopupPack> findAllByOrderBySortOrderAsc();
}
