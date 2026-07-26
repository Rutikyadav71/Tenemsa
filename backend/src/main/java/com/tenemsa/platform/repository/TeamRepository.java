package com.tenemsa.platform.repository;
import com.tenemsa.platform.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface TeamRepository extends JpaRepository<Team,Long> {
    List<Team> findByMemberIdsContaining(int empId);
}