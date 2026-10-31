package com.tenemsa.platform.notification.repository;

import com.tenemsa.platform.notification.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByCompanyIdAndEmpId(Long companyId, Integer empId);
    List<NotificationPreference> findByCompanyIdAndEmpIdIn(Long companyId, Collection<Integer> empIds);
}
