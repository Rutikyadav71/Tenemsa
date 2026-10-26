package com.tenemsa.platform.project.repository;

import com.tenemsa.platform.project.model.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every finder is company-scoped (tenant isolation). */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Project> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndProjectKey(Long companyId, String projectKey);

    /** Row-locking read used while numbering a new issue, so two simultaneous creates cannot get the same number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.companyId = :companyId")
    Optional<Project> lockByIdAndCompanyId(@Param("id") Long id, @Param("companyId") Long companyId);
}
