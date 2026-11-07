package com.tenemsa.platform.announcement.repository;

import com.tenemsa.platform.announcement.model.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    Page<Announcement> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
    long countByCompanyIdAndCreatedAtAfter(Long companyId, LocalDateTime since);
}
