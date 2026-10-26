package com.tenemsa.platform.project.issue.repository;

import com.tenemsa.platform.project.issue.model.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Every finder is company-scoped (tenant isolation). */
public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    List<IssueComment> findByCompanyIdAndIssueIdOrderByIdAsc(Long companyId, Long issueId);

    @Modifying
    @Query("DELETE FROM IssueComment c WHERE c.companyId = :companyId AND c.issueId = :issueId")
    void deleteAllForIssue(@Param("companyId") Long companyId, @Param("issueId") Long issueId);

    @Modifying
    @Query("DELETE FROM IssueComment c WHERE c.companyId = :companyId AND c.issueId IN " +
            "(SELECT i.id FROM Issue i WHERE i.companyId = :companyId AND i.projectId = :projectId)")
    void deleteAllInProject(@Param("companyId") Long companyId, @Param("projectId") Long projectId);
}
