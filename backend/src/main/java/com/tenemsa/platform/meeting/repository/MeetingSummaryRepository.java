package com.tenemsa.platform.meeting.repository;

import com.tenemsa.platform.meeting.model.MeetingSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Every finder takes companyId first. */
public interface MeetingSummaryRepository extends JpaRepository<MeetingSummary, Long> {

    Optional<MeetingSummary> findByIdAndCompanyId(Long id, Long companyId);

    List<MeetingSummary> findByCompanyIdOrderByCreatedAtDescIdDesc(Long companyId);

    List<MeetingSummary> findByCompanyIdAndCreatedByEmpIdOrderByCreatedAtDescIdDesc(Long companyId, Integer createdByEmpId);
}
