package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Notification;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByCompanyId(Long companyId, Sort sort);

    // Previously "UPDATE Notification n SET n.read = true" with no WHERE —
    // one company pressing "mark all read" flipped every other tenant's too.
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.companyId = :companyId")
    void markAllReadForCompany(@Param("companyId") Long companyId);
}
