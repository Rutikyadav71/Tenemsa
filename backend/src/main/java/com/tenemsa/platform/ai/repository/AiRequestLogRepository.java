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

    /** Chat quotas ignore knowledge-base embedding rows (they have their own cap) and refused calls. */
    @Query("select count(l) from AiRequestLog l where l.companyId = :companyId and l.empId = :empId and l.createdAt > :after "
            + "and l.status <> 'RATE_LIMITED' and l.feature <> 'KB_EMBEDDING'")
    long countChatByUser(@Param("companyId") Long companyId, @Param("empId") Integer empId, @Param("after") LocalDateTime after);

    @Query("select count(l) from AiRequestLog l where l.companyId = :companyId and l.createdAt > :after "
            + "and l.status <> 'RATE_LIMITED' and l.feature <> 'KB_EMBEDDING'")
    long countChatByCompany(@Param("companyId") Long companyId, @Param("after") LocalDateTime after);

    /** Embedding requests (one row per batch sent to the provider) in the window, for the knowledge-base daily cap. */
    @Query("select count(l) from AiRequestLog l where l.companyId = :companyId and l.createdAt > :after "
            + "and l.status <> 'RATE_LIMITED' and l.feature = 'KB_EMBEDDING'")
    long countEmbeddingsByCompany(@Param("companyId") Long companyId, @Param("after") LocalDateTime after);

    @Query("select l from AiRequestLog l where l.companyId = :companyId and l.createdAt >= :since order by l.createdAt desc")
    List<AiRequestLog> findSince(@Param("companyId") Long companyId, @Param("since") LocalDateTime since);
}
