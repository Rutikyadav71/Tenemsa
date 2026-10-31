package com.tenemsa.platform.notification.repository;

import com.tenemsa.platform.notification.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every query starts from the company. Personal notifications are also filtered by recipient. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByCompanyId(Long companyId, Sort sort);

    /** Mine, plus (when includeLegacy) the older company-wide ones that have no recipient. */
    @Query("SELECT n FROM Notification n WHERE n.companyId = :companyId AND " +
            "(n.recipientEmpId = :empId OR (:includeLegacy = true AND n.recipientEmpId IS NULL)) ORDER BY n.createdAt DESC, n.id DESC")
    List<Notification> findVisible(@Param("companyId") Long companyId, @Param("empId") Integer empId,
                                   @Param("includeLegacy") boolean includeLegacy, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.companyId = :companyId AND n.read = false AND " +
            "(n.recipientEmpId = :empId OR (:includeLegacy = true AND n.recipientEmpId IS NULL))")
    long countUnread(@Param("companyId") Long companyId, @Param("empId") Integer empId, @Param("includeLegacy") boolean includeLegacy);

    @Query("SELECT n FROM Notification n WHERE n.id = :id AND n.companyId = :companyId AND " +
            "(n.recipientEmpId = :empId OR (:includeLegacy = true AND n.recipientEmpId IS NULL))")
    Optional<Notification> findVisibleById(@Param("id") Long id, @Param("companyId") Long companyId,
                                           @Param("empId") Integer empId, @Param("includeLegacy") boolean includeLegacy);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.companyId = :companyId AND n.read = false AND " +
            "(n.recipientEmpId = :empId OR (:includeLegacy = true AND n.recipientEmpId IS NULL))")
    void markAllRead(@Param("companyId") Long companyId, @Param("empId") Integer empId, @Param("includeLegacy") boolean includeLegacy);
}
