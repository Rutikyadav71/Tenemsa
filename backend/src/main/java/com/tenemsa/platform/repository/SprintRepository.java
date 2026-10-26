package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Sprint;
import com.tenemsa.platform.model.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Every finder is company-scoped (tenant isolation). */
public interface SprintRepository extends JpaRepository<Sprint, Long> {

    List<Sprint> findByCompanyIdAndProjectIdOrderByIdAsc(Long companyId, Long projectId);

    Optional<Sprint> findByIdAndCompanyId(Long id, Long companyId);

    List<Sprint> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    boolean existsByCompanyIdAndProjectIdAndStatus(Long companyId, Long projectId, SprintStatus status);

    @Modifying
    @Query("DELETE FROM Sprint s WHERE s.companyId = :companyId AND s.projectId = :projectId")
    void deleteAllInProject(@Param("companyId") Long companyId, @Param("projectId") Long projectId);
}
