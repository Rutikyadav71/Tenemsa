package com.tenemsa.platform.announcement.repository;

import com.tenemsa.platform.announcement.model.PlatformAnnouncement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface PlatformAnnouncementRepository extends JpaRepository<PlatformAnnouncement, Long> {
    Page<PlatformAnnouncement> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<PlatformAnnouncement> findByWithdrawnFalseAndExpiresAtAfterOrderByCreatedAtDesc(LocalDateTime now);
}
