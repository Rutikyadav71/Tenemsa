package com.tenemsa.platform.repository;
import com.tenemsa.platform.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** Every finder is company-scoped (tenant isolation, Phase 4). */
public interface TeamRepository extends JpaRepository<Team,Long> {
    List<Team> findByCompanyId(Long companyId);
    Optional<Team> findByIdAndCompanyId(Long id, Long companyId);
    List<Team> findByCompanyIdAndMemberIdsContaining(Long companyId, Integer empId);
    List<Team> findByCompanyIdAndTeamLeadId(Long companyId, Integer teamLeadId);
}
