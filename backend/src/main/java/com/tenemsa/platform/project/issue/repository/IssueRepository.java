package com.tenemsa.platform.repository;

import com.tenemsa.platform.model.Issue;
import com.tenemsa.platform.model.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Every finder is company-scoped (tenant isolation). */
public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findByCompanyIdAndProjectIdOrderByIdAsc(Long companyId, Long projectId);

    Optional<Issue> findByIdAndCompanyId(Long id, Long companyId);

    List<Issue> findByCompanyIdAndAssigneeEmpIdAndStatusNotOrderByDueDateAscIdAsc(Long companyId, Integer assigneeEmpId, IssueStatus status);

    /** Rows of [projectId, status, count] for the project list's progress bars. */
    @Query("SELECT i.projectId, i.status, COUNT(i) FROM Issue i WHERE i.companyId = :companyId GROUP BY i.projectId, i.status")
    List<Object[]> countByProjectAndStatus(@Param("companyId") Long companyId);

    List<Issue> findByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);

    @Modifying
    @Query("DELETE FROM Issue i WHERE i.companyId = :companyId AND i.projectId = :projectId")
    void deleteAllInProject(@Param("companyId") Long companyId, @Param("projectId") Long projectId);
}
