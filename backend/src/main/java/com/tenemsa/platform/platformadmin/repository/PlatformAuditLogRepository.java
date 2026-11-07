package com.tenemsa.platform.platformadmin.repository;

import com.tenemsa.platform.platformadmin.model.PlatformAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, Long> {
    Page<PlatformAuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
    Page<PlatformAuditLog> findByActionOrderByCreatedAtDescIdDesc(String action, Pageable pageable);
}
