package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every finder takes companyId first: the audit trail is tenant data too. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByCompanyIdOrderByCreatedAtDescIdDesc(Long companyId, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndActionOrderByCreatedAtDescIdDesc(Long companyId, String action, Pageable pageable);
}
