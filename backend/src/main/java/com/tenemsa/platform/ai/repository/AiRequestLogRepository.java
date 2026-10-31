package com.tenemsa.platform.ai.repository;

import com.tenemsa.platform.ai.model.AiRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Every finder takes companyId first: usage data is tenant data. */
public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {

    /** Calls that count towards quotas: everything except those refused for being over quota. */
    long countByCompanyIdAndEmpIdAndCreatedAtAfterAndStatusNot(Long companyId, Integer empId, LocalDateTime after, String status);

    long countByCompanyIdAndCreatedAtAfterAndStatusNot(Long companyId, LocalDateTime after, String status);

    @Query("select l from AiRequestLog l where l.companyId = :companyId and l.createdAt >= :since order by l.createdAt desc")
    List<AiRequestLog> findSince(@Param("companyId") Long companyId, @Param("since") LocalDateTime since);
}
