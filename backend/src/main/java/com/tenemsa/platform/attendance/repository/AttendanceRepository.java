package com.tenemsa.platform.repository;
import com.tenemsa.platform.model.Attendance;
import com.tenemsa.platform.model.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

/** Every finder takes companyId first: empId is unique only within a company (tenant isolation, Phase 4). */
public interface AttendanceRepository extends JpaRepository<Attendance,Long> {
    List<Attendance> findByCompanyIdAndEmpId(Long companyId, int empId);
    List<Attendance> findByCompanyIdAndDate(Long companyId, LocalDate date);
    List<Attendance> findByCompanyIdAndEmpIdAndDateBetween(Long companyId, int empId, LocalDate start, LocalDate end);
    List<Attendance> findByCompanyIdAndDateBetween(Long companyId, LocalDate start, LocalDate end);
    Optional<Attendance> findByCompanyIdAndEmpIdAndDate(Long companyId, int empId, LocalDate date);
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.companyId=:companyId AND a.empId=:empId AND a.status=:status AND a.date BETWEEN :start AND :end")
    long countByStatusAndMonth(@Param("companyId") Long companyId, @Param("empId") int empId, @Param("status") AttendanceStatus status,
        @Param("start") LocalDate start, @Param("end") LocalDate end);
}
